/**
 * Copyright (C) 2016-2018 Florian Heck
 * Copyright (C) 2019-2020 Thomas Bock
 * Copyright (C) 2025 Leo Sendelbach
 *
 * This file is part of GitHubWrapper.
 *
 * GitHubWrapper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GitHubWrapper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GitWrapper. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.gitwrapper;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.gitwrapper.GitHubRepository.IssueDataCached;
import io.gsonfire.PostProcessor;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The IssueDataPostprocessor extracts additional information from issues and populated IssueData instances with other
 * git-related information.
 */
public class IssueDataProcessor implements JsonDeserializer<IssueDataCached>, PostProcessor<IssueData> {

    private static final JsonParser parser = new JsonParser();

    private Map<Integer, IssueData> cache = new ConcurrentHashMap<>();
    private Map<Integer, IssueData> workingQueue = new ConcurrentHashMap<>();

    private final GitHubRepository repo;
    private final String issueBaseUrl;

    /**
     * Creates a new IssueDataProcessor for handling Issues specific to the provided GitHubRepository.
     *
     * @param repo
     *         the repository
     */
    IssueDataProcessor(GitHubRepository repo, String issueBaseUrl) {
        this.repo = repo;
        this.issueBaseUrl = issueBaseUrl;
    }

    /**
     * Parses Commits from issue body, comment bodies, reviews, reviews' comments, and referenced events.
     *
     * @param issue
     *         the IssueData
     * @return a List of all referenced Commits
     */
    private List<ReferencedLink<GitHubCommit>> parseCommits(IssueData issue) {

        // Parse commits from comments
        Stream<ReferencedLink<List<String>>> commentCommits = issue.getCommentsList().stream().map(comment ->
                        new ReferencedLink<>(extractSHA1s(comment.target), comment.user, comment.referenced_at, "commitMentionedInIssue"));

        // Parse commits from referenced commits
        Stream<ReferencedLink<List<String>>> referencedCommits = issue.getEventsList().stream()
                .filter(eventData -> eventData instanceof EventData.ReferencedEventData)
                // filter out errors from referencing commits
                .filter(eventData -> ((EventData.ReferencedEventData) eventData).commit != null)
                .map(eventData -> {
                    if (((GitHubCommit) ((EventData.ReferencedEventData) eventData).commit).isExternal()) {
                        return new ReferencedLink<>(Collections.singletonList(((EventData.ReferencedEventData) eventData).commit.getId()), eventData.user, eventData.created_at, "commitReferencesIssueExternal");
                    } else {
                        return new ReferencedLink<>(Collections.singletonList(((EventData.ReferencedEventData) eventData).commit.getId()), eventData.user, eventData.created_at, "commitReferencesIssue");
                    }
                });

        // Parse commits from reviews and reviews' comments
        if (issue.isPullRequest()) {
            Stream<ReferencedLink<List<String>>> reviewsCommentCommits = null;

            for (ReviewData review :issue.getReviewsList()) {
                Stream<ReferencedLink<List<String>>> reviewCommentCommits = review.getReviewComments().stream().map(comment ->
                                new ReferencedLink<>(extractSHA1s(comment.target.getBody()), comment.user, comment.referenced_at, "commitMentionedInIssue"));
                if (reviewsCommentCommits != null) {
                    reviewsCommentCommits = Stream.concat(reviewsCommentCommits, reviewCommentCommits);
                } else {
                    reviewsCommentCommits = reviewCommentCommits;
                }
            }

            Stream<ReferencedLink<List<String>>> reviewInitialCommentCommits = issue.getReviewsList().stream().map(review -> {
                            if (review.hasReviewInitialComment()) {
                                return new ReferencedLink<>(extractSHA1s(((ReviewData.ReviewInitialCommentData) review).body), review.user, review.submitted_at, "commitMentionedInIssue");
                            } else {
                                return new ReferencedLink<>(new ArrayList<>(), review.user, review.submitted_at);
                            }
            });

            if (reviewsCommentCommits == null) {
               commentCommits = Stream.concat(commentCommits, reviewInitialCommentCommits);
            } else {
               commentCommits = Stream.concat(commentCommits, Stream.concat(reviewsCommentCommits, reviewInitialCommentCommits));
           }
        }

        // Parse commits from dismissal messages of "review_dismissed" events
        Stream<ReferencedLink<List<String>>> dismissalCommentCommits = issue.getEventsList().stream().map(event -> {
                        if (event.getEvent() == "review_dismissed") {
                            return new ReferencedLink<>(extractSHA1s(((EventData.DismissedReviewEventData) event).dismissalMessage), event.user, event.created_at, "commitMentionedInIssue");
                        } else {
                            return new ReferencedLink<>(new ArrayList<>(), event.user, event.created_at);
                        }
        });

        commentCommits = Stream.concat(commentCommits, dismissalCommentCommits);

        // Parse commits from issue body and concat it with all matches from above
        return Stream.concat(Stream.concat(commentCommits, referencedCommits), Stream.of(new ReferencedLink<>(extractSHA1s(issue.body), issue.user, issue.created_at, "commitMentionedInIssue")))
                .flatMap(commentEntries -> commentEntries.target.stream()
                        .map(repo::getGithubCommit)
                        // filter out false positive matches on normal words (and other errors)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .distinct()
                        .map(target -> new ReferencedLink<>(target, commentEntries.user, commentEntries.referenced_at, commentEntries.type)))
                .collect(Collectors.toList());
    }

    /**
     * Parse issues from issue body, comment bodies, reviews, reviews' comments, and referenced events.
     *
     * @param issue
     *         the Issue to analyze
     * @param gson
     *         the Gson used to deserialize
     * @return a list of all Issue numbers that are referenced in issue body, comment bodies, reviews, reviews' comments,
     *         and referenced events.
     */
    List<ReferencedLink<Integer>> parseIssues(IssueData issue, Gson gson) {

        // Parse issues from comments
        Stream<ReferencedLink<List<String>>> commentIssues = issue.getCommentsList().stream().map(comment ->
                new ReferencedLink<>(extractHashtags(comment.getTarget(), true), comment.user, comment.referenced_at));

        // to get real reference events, the timeline api needs to be incorporated. Since it is still in preview as of
        // 2018-04, I have not implemented it.
        // For details and links: https://gist.github.com/dahlbyk/229f6ee762e2b0b45f3add7c2459e64a

        // Parse issues from reviews and reviews' comments
        if (issue.isPullRequest()) {
            Stream<ReferencedLink<List<String>>> reviewsCommentsIssues = null;

            for (ReviewData review :issue.getReviewsList()) {
                Stream<ReferencedLink<List<String>>> reviewCommentsIssues = review.getReviewComments().stream().map(comment ->
                        new ReferencedLink<>(extractHashtags(comment.target.getBody(), true), comment.user, comment.referenced_at));
                if (reviewsCommentsIssues != null) {
                    reviewsCommentsIssues = Stream.concat(reviewsCommentsIssues, reviewCommentsIssues);
                } else {
                    reviewsCommentsIssues = reviewCommentsIssues;
                }
            }

            Stream<ReferencedLink<List<String>>> reviewInitialCommentsIssues = issue.getReviewsList().stream().map(review -> {
                    if (review.hasReviewInitialComment()) {
                        return new ReferencedLink<>(extractHashtags(((ReviewData.ReviewInitialCommentData) review).body, true), review.user, review.submitted_at);
                    } else {
                        return new ReferencedLink<>(new ArrayList<>(), review.user, review.submitted_at);
                    }
            });

            if (reviewsCommentsIssues == null) {
               commentIssues = Stream.concat(commentIssues, reviewInitialCommentsIssues);
            } else {
               commentIssues = Stream.concat(commentIssues, Stream.concat(reviewsCommentsIssues, reviewInitialCommentsIssues));
            }
        }

        // Parse issues from dismissal messages of "review_dismissed" events
        Stream<ReferencedLink<List<String>>> dismissalCommentIssues = issue.getEventsList().stream().map(event -> {
                if (event.getEvent() == "review_dismissed") {
                    return new ReferencedLink<>(extractHashtags(((EventData.DismissedReviewEventData) event).dismissalMessage, true), event.user, event.created_at);
                } else {
                    return new ReferencedLink<>(new ArrayList<>(), event.user, event.created_at);
                }
        });

        commentIssues = Stream.concat(commentIssues, dismissalCommentIssues);

        // Parse issues from issue body and concat it with all matches from above
        return Stream.concat(commentIssues, Stream.of(new ReferencedLink<>(extractHashtags(issue.body, true), issue.user, issue.created_at)))
                .flatMap(commentEntries -> commentEntries.target.stream()
                        .map(link -> {
                            int num;
                            try {
                                num = Integer.parseInt(link);
                            } catch (NumberFormatException e) {
                                //noinspection ConstantConditions Reason: type inference
                                return Optional.ofNullable((Integer) null);
                            }

                            // again, short-circuit, if we have a cache hit
                            IssueData cached = cache.get(num);
                            if (cached != null) {
                                return Optional.of(cached.getNumber());
                            }

                            Optional<String> refIssue = repo.getJSONStringFromURL(issueBaseUrl + link);
                            try {
                                return refIssue.map(s -> (((IssueData) gson.fromJson(s, new TypeToken<IssueDataCached>() {}.getType())).getNumber()));
                            } catch (NullPointerException e) {
                                // refIssue seems to be not existent, don't return an issue here
                                return Optional.ofNullable((Integer) null);
                            }
                        })
                        // filter out false positive matches on normal words (and other errors)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .distinct()
                        .map(target -> new ReferencedLink<>(target, commentEntries.user, commentEntries.referenced_at)))
                .collect(Collectors.toList());
    }

    /**
     * Extracts theoretically valid commit hashes from text.
     *
     * @param text
     *         the text to analyze
     * @return a List of all valid hashes
     */
    private List<String> extractSHA1s(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        Pattern sha1Pattern = Pattern.compile("([0-9a-f]{5,40})");
        Matcher matcher = sha1Pattern.matcher(text);

        List<String> sha1s = new ArrayList<>();

        while (matcher.find()) {
            sha1s.add(matcher.group(1));
        }

        return sha1s;
    }

    /**
     * Extracts theoretically valid issue links from text.
     *
     * @param text
     *         the text to analyze
     * @param onlyInSameRepo
     *         whether to only check for hashtags pointing to the same repository
     * @return a List of all valid hashtags
     */
    private List<String> extractHashtags(String text, boolean onlyInSameRepo) {
        if (text == null) {
            return Collections.emptyList();
        }
        Pattern hashtagPattern;

        // filter out everything in code block
        String[] texts = text.split("```");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            if (i % 2 == 0) {
                sb.append(texts[i]);
            }
        }
        text = sb.toString();

        if (onlyInSameRepo) {
            String repoName = repo.getRepoName();
            String repoUser = repo.getRepoUser();

            /* There are several possible patterns:
             * (1) #number
             * (2) repoUser#number
             * (3) repoUser/repoName#number
	     * (4) /pull/number
	     * (5) repoUser/pull/number
	     * (6) repoUser/repoName/pull/number
	     * (7) /issues/number
	     * (9) repoUser/issues/number
	     * (10) repoUser/repoName/issues/number
             */
            hashtagPattern = Pattern.compile(String.format("(%s/%s|%s|^|\\s+)(#|/pull/|/issues/)([0-9]{1,11})",
                                                           repoUser, repoName, repoUser));
        } else {
            hashtagPattern = Pattern.compile("#([0-9]{1,11})");
        }

        Matcher matcher = hashtagPattern.matcher(text);
        List<String> hashtags = new ArrayList<>();

        while (matcher.find()) {
        String match;

            if (onlyInSameRepo) {
                // Just keep group 3, that is, keep everything after the #
                match = matcher.group(3);
            } else {
                match = matcher.group(1);
            }
            hashtags.add(match);
        }

        return hashtags;
    }

    @Override
    public void postSerialize(JsonElement result, IssueData src, Gson gson) { }

    @Override
    public IssueDataCached deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        int number = json.getAsJsonObject().get("number").getAsJsonPrimitive().getAsNumber().intValue();
        IssueData cached = cache.get(number);
        // check cache -> update and short circuit
        if (cached != null) {
            if (!json.getAsJsonObject().get("body").isJsonNull()) {
                cached.body = json.getAsJsonObject().get("body").getAsString();
            } else {
                cached.body = "";
            }
            if (!json.getAsJsonObject().get("body").isJsonNull()) {
                cached.title = json.getAsJsonObject().get("title").getAsString();
            } else {
                cached.title = "";
            }
            cached.repo = repo;
            return cached;
        }

        // check if pr directly from github
        JsonElement pr = json.getAsJsonObject().get("pull_request");
        if (pr != null) {
            // get additional data
            return repo.getJSONStringFromURL(pr.getAsJsonObject().get("url").getAsString()).map(data -> {
                PullRequestData result = context.deserialize(parser.parse(data), new TypeToken<PullRequestData>() {}.getType());
                result.repo = repo;
                return result;
            }).orElse(null);
        }

        // check if pr from local dump, we then already have all data
        pr = json.getAsJsonObject().get("isPullRequest");
        if (pr != null && pr.getAsBoolean()) {
            PullRequestData result = context.deserialize(json, new TypeToken<PullRequestData>() {}.getType());
            result.repo = repo;
            return result;
        }

        // normal issue from github or dump
        IssueData result = context.deserialize(json, new TypeToken<IssueData>() {}.getType());
        result.repo = repo;
        return result;
    }

    @Override
    public void postDeserialize(IssueData result, JsonElement src, Gson gson) {
        // check if currently worked on, to return early and break cyclic dependencies
        IssueData workingOn = workingQueue.get(result.number);
        if (workingOn != null) return;
        workingQueue.put(result.number, result);
        cache.put(result.number, result);

        IssueData lookup = result;
        if (result.isPullRequest) {
            // we need the actual issue data back, because events and comments are missing in a pr
            JsonElement issueSource = parser.parse(repo.getJSONStringFromURL(src.getAsJsonObject().get("issue_url").getAsString())
                    .orElseThrow(() -> new JsonParseException("Could not get Issue data for this PR: " + result.url)));
            lookup = gson.fromJson(issueSource, new TypeToken<IssueDataCached>() {}.getType());

            PullRequestData prResult = (PullRequestData) result;
            if (prResult.head.repo != null) {
                prResult.branch = prResult.head.repo.full_name + "/" + prResult.head.ref;
            } else {
                prResult.branch = prResult.head.sha;
            }
        }

        // fill in missing data
        result.state = State.getFromString(src.getAsJsonObject().get("state").getAsString());

        if (result.getCommentsList() == null) {
            Optional<List<ReferencedLink<String>>> comments = repo.getComments(lookup);
            result.setComments(comments.orElse(Collections.emptyList()));
        }

        if (result.getEventsList() == null) {
            Optional<List<EventData>> events = repo.getEvents(lookup);
            result.setEvents(events.orElse(Collections.emptyList()));
        }

        if (result.getReviewsList() == null && result.isPullRequest) {
            Optional<List<ReviewData>> reviews = repo.getReviews(lookup);
            result.setReviews(reviews.orElse(Collections.emptyList()));
        }

        if (result.getRelatedCommits() == null) {
            List<ReferencedLink<GitHubCommit>> commits = parseCommits(result);
            if (result.isPullRequest) {
                Optional<String> json = repo.getJSONStringFromURL(src.getAsJsonObject().get("commits_url").getAsString());
                //noinspection unchecked
                json.ifPresent(data -> commits.addAll(
                        ((List<GitHubCommit>) gson.fromJson(data, new TypeToken<ArrayList<GitHubCommit>>() {}.getType())).stream().map(c -> {
                            // Try to get committer data
                            UserData user = new UserData();
                            user.email = c.getCommitterMail();
                            user.name = c.getCommitter();
                            user.username = c.getCommitterUsername();
                            OffsetDateTime time = c.getCommitterTime();
                            // otherwise get author data, close enough
                            if (user.email == null && user.name == null && time == null) {
                                user.email = c.getAuthorMail();
                                user.name = c.getAuthor();
                                time = c.getAuthorTime();
                                user.username = c.getAuthorUsername();
                            }
                            // if it still fails, make sure that we have data, even if it's just fomr the issue
                            if (user.email == null && user.name == null && time == null) {
                                user = result.user;
                                time = result.created_at;
                            }

                            return new ReferencedLink<>(c, user, time, "commitAddedToPullRequest");
                        }).collect(Collectors.toList())));
            }

            result.setRelatedCommits(commits);
        }

        if (result.relatedIssues == null) {
            result.setRelatedIssues(parseIssues(result, gson));
        }

        workingQueue.remove(result.number);
    }

    /**
     * Manually adds Issues to the cache used for short-circuiting deserialization.
     *
     * @param protoCache
     *         a list of IssueData elements
     */
    void addCache(List<IssueData> protoCache) {
        cache.putAll(protoCache.stream().collect(Collectors.toMap(issue -> issue.number, issue -> issue)));
    }

    /**
     * Gets a list of all cached issues.
     *
     * @return a collection of all cached issues
     */
    Map<Integer, IssueData> getCache() {
        return cache;
    }
}
