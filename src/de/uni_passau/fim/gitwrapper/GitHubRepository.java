/**
 * Copyright (C) 2016-2020 Florian Heck
 * Copyright (C) 2018 Claus Hunsen
 * Copyright (C) 2019-2021 Thomas Bock
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.processexecutor.ProcessExecutor;
import io.gsonfire.GsonFireBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A GitHubRepository wraps a (local) Repository to give access to the GitHub API to provide {@link PullRequestData} and
 * {@link IssueData}.
 */
public class GitHubRepository extends Repository {

    private static final Logger LOG = Logger.getLogger(GitHubRepository.class.getCanonicalName());

    private static final Set<Token> tokens = new HashSet<>();
    private static final Queue<Thread> tokenWaitList = new ConcurrentLinkedQueue<>();

    private final Gson gson;
    private final CloseableHttpClient hc;
    private IssueDataProcessor issueProcessor;

    private final Pattern commitPattern = Pattern.compile("([0-9a-f]{40})\n(.*?)\nhash=", Pattern.DOTALL);
    private final String apiBaseURL;
    private final String repoName;
    private final String repoUser;
    private List<PullRequest> pullRequests;
    private List<IssueData> issues;
    private Map<String, GitHubCommit> unknownCommits = new ConcurrentHashMap<>();
    private Map<String, Optional<GitHubCommit>> checkedHashes = new ConcurrentHashMap<>();

    private final AtomicBoolean allowGuessing = new AtomicBoolean(false);
    private final AtomicBoolean sleepOnApiLimit = new AtomicBoolean(true);
    private final AtomicBoolean offline = new AtomicBoolean(false);

    private final ForkJoinPool threadPool;

    /**
     * Create a repository with additional information about GitHub hosted repositories.
     *
     * @param url
     *         the URL of the repository on GitHub
     * @param dir
     *         the directory where the local repository is located
     * @param git
     *         the GitWrapper instance to use
     */
    public GitHubRepository(String url, File dir, GitWrapper git) {
        this(url, dir, git, "");
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories.
     *
     * @param repo
     *         an existing repository cloned from GitHub
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     */
    public GitHubRepository(Repository repo, List<String> oauthToken) {
        this(repo.getUrl(), repo.getDir(), repo.getGit(), oauthToken);
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories.
     *
     * @param url
     *         the URL of the repository on GitHub
     * @param dir
     *         the directory where the local repository is located
     * @param git
     *         the GitWrapper instance to use
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     */
    public GitHubRepository(String url, File dir, GitWrapper git, String oauthToken) {
        this(url, dir, git, Collections.singletonList(oauthToken));
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories. The given file must contain a
     * JSON dump of the list of corresponding issues from GitHub.
     *
     * @param repo
     *         an existing repository cloned from GitHub
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     * @param issueCache
     *         the File containing the issue cache
     * @throws FileNotFoundException
     *         if {@code issueCache} is not found
     * @see #serialize(Object)
     */
    public GitHubRepository(Repository repo, List<String> oauthToken, File issueCache) throws FileNotFoundException {
        this(repo.getUrl(), repo.getDir(), repo.getGit(), oauthToken, issueCache);
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories. The given file must contain a
     * JSON dump of the list of corresponding issues from GitHub.
     *
     * @param url
     *         the URL of the repository on GitHub
     * @param dir
     *         the directory where the local repository is located
     * @param git
     *         the GitWrapper instance to use
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     * @param issueCache
     *         the File containing the issue cache
     * @throws FileNotFoundException
     *         if {@code issueCache} is not found
     * @see #serialize(Object)
     */
    public GitHubRepository(String url, File dir, GitWrapper git, List<String> oauthToken, File issueCache) throws FileNotFoundException {
        this(url, dir, git, oauthToken);

        if (issueProcessor == null) {
            issueProcessor = new IssueDataProcessor(this, apiBaseURL + "/issues/");
        }
        GsonBuilder gb = new GsonFireBuilder().createGsonBuilder();
        gb.registerTypeAdapter(Commit.class, new CommitProcessor(this, new UserDataProcessor(this)));
        gb.registerTypeAdapter(GitHubCommit.class, new GitHubCommitProcessor(this, new UserDataProcessor(this)));
        gb.registerTypeAdapter(IssueDataCached.class, issueProcessor);
        gb.registerTypeAdapter(ReferencedLink.class, new ReferencedLinkProcessor(this));
        gb.registerTypeAdapter(EventData.class, new EventDataProcessor());
        gb.registerTypeAdapter(ReviewData.class, new ReviewDataProcessor());
        gb.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimerProcessor());
        gb.setDateFormat("yyyy-MM-dd HH:mm:ss");
        gb.serializeNulls();
        Gson tempGson = gb.create();

        // read cache and fill up missing issue data
        offline.set(true);
        List<IssueData> issues = new ArrayList<>(tempGson.fromJson(new BufferedReader(new FileReader(issueCache)), new TypeToken<List<IssueDataCached>>() {}.getType()));
        offline.set(false);
        issueProcessor.addCache(issues);
        issues.forEach(IssueData::freeze);

        this.issues = issues;
//        getPullRequests();
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories.
     *
     * @param url
     *         the URL of the repository on GitHub
     * @param dir
     *         the directory where the local repository is located
     * @param git
     *         the GitWrapper instance to use
     * @param oauthToken
     *         a list of valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     */
    public GitHubRepository(String url, File dir, GitWrapper git, List<String> oauthToken) {
        super(git, url, dir);
        if (url.contains("git@")) {
            url = url.replace(":", "/").replace("git@", "https://");
        }
        apiBaseURL = url.replace(".git", "").replace("//github.com/", "//api.github.com/repos/");
        LOG.fine(String.format("Creating repo for %s", apiBaseURL));

        this.repoName = getRepoNameFromUrl(this.getUrl());
        this.repoUser = getRepoUserFromUrl(this.getUrl());

        synchronized (tokens) {
            oauthToken.stream().map(Token::new).forEach(tokens::add);
        }

        if (issueProcessor == null) {
            issueProcessor = new IssueDataProcessor(this, apiBaseURL + "/issues/");
        }
        GsonFireBuilder gfb = new GsonFireBuilder();
        UserDataProcessor userProcessor = new UserDataProcessor(this);
        ReferencedLinkProcessor referencedLinkProcessor = new ReferencedLinkProcessor(this);
        gfb.registerPostProcessor(IssueData.class, issueProcessor);
        gfb.registerPostProcessor(ReferencedLink.class, referencedLinkProcessor);
        gfb.registerPostProcessor(EventData.ReferencedEventData.class, new EventDataProcessor.ReferencedEventProcessor(this));
        gfb.registerPostProcessor(EventData.LabeledEventData.class, new EventDataProcessor.LabeledEventProcessor());
        gfb.registerPostProcessor(EventData.DismissedReviewEventData.class, new EventDataProcessor.DismissedReviewEventProcessor());
        gfb.registerPostProcessor(EventData.AssignedEventData.class, new EventDataProcessor.AssignedEventProcessor());
        gfb.registerPostProcessor(ReviewData.ReviewInitialCommentData.class, new ReviewDataProcessor.ReviewInitialCommentDataProcessor(this));
        GsonBuilder gb = gfb.createGsonBuilder();
        gb.registerTypeAdapter(Commit.class, new CommitProcessor(this, userProcessor));
        gb.registerTypeAdapter(GitHubCommit.class, new GitHubCommitProcessor(this, userProcessor));
        gb.registerTypeAdapter(IssueDataCached.class, issueProcessor);
        gb.registerTypeAdapter(ReferencedLink.class, referencedLinkProcessor);
        gb.registerTypeAdapter(EventData.class, new EventDataProcessor());
        gb.registerTypeAdapter(UserData.class, userProcessor);
        gb.registerTypeAdapter(ReviewData.class, new ReviewDataProcessor());
        gb.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimerProcessor());
        gb.setDateFormat("yyyy-MM-dd HH:mm:ss");
        gb.serializeNulls();
        gson = gb.create();

        hc = HttpClients.createDefault();

        threadPool = new ForkJoinPool(oauthToken.size());
    }

    /**
     * Determines the name of the repository from a given URL.
     *
     * @return the name of the repository
     */
    private static String getRepoNameFromUrl(String url) {
        String[] parts = url.split("/");
        String repoName = parts[parts.length - 1];

        // remove the ".git" suffix
        if (repoName.endsWith(".git")) {
            repoName = repoName.substring(0, repoName.length() - 4);
        }
        return repoName;
    }

    /**
     * Determines the user or organization a repository belongs to from a given URL.
     *
     * @return the name of the user or organization
     */
    private static String getRepoUserFromUrl(String url) {
        String[] parts = url.split("/");
        String user = parts[parts.length - 2];
        return user;
    }

    /**
     * Gets the name of the repository.
     *
     * @return the name of the repository
     */
    public String getRepoName() {
        return this.repoName;
    }

    /**
     * Gets the name of user or organization the repository belongs to.
     *
     * @return the name of the user or organization
     */
    public String getRepoUser() {
        return this.repoUser;
    }

    /**
     * Gets a List of PullRequests.
     *
     * @param filter
     *         The state of the PullRequests to include (see {@link State#includes(State, State)})
     * @return optionally a List of PullRequests or an empty Optional, if an error occurred
     */
    public Optional<List<PullRequest>> getPullRequests(State filter) {
        getPullRequests();
        if (pullRequests == null) {
            LOG.warning("Could not get PRs.");
            return Optional.empty();
        }
        return Optional.of(pullRequests.stream().filter(pr -> State.includes(pr.getState(), filter)).collect(Collectors.toList()));
    }

    /**
     * Gets a List of Issues.
     *
     * @param includePullRequests
     *         if {@code true}, will include {@link PullRequestData PullRequests} as well
     * @return optionally a List of IssueData or an empty Optional if an error occurred
     */
    public Optional<List<IssueData>> getIssues(boolean includePullRequests) {
        return getIssues(includePullRequests, null);
    }

    /**
     * Gets a List of Issues. Use the {@code updateSince} parameter to request updates (to cached data). Please note,
     * that this will not remove data deleted at GitHub.
     *
     * @param includePullRequests
     *         if {@code true}, will include {@link PullRequestData PullRequests} as well
     * @param updateSince
     *         if not {@code null}, will update all issues added or with changes since the given date
     * @return optionally a List of IssueData or an empty Optional if an error occurred
     */
    public Optional<List<IssueData>> getIssues(boolean includePullRequests, OffsetDateTime updateSince) {
        if (issues == null || updateSince != null) {
            String timeLimit;
            Type type = new TypeToken<IssueDataCached>() {}.getType();
            if (updateSince != null) {
                timeLimit = "&since=" + updateSince.format(DateTimeFormatter.ISO_DATE_TIME);
                type = new TypeToken<IssueData>() {}.getType();
            }
            else timeLimit = "";
            Type finalType = type;
            // For debugging, you may add additional parameters to the string. For example, '/issues?creator=sleo&state=all'
            // will fetch issues created by user 'sleo' and all related issues and commits.
            getJSONStringFromPath("/issues?state=all" + timeLimit).map(json -> {
                List<IssueData> data;
                try {
                    ArrayList<JsonElement> list = gson.fromJson(json, new TypeToken<ArrayList<JsonElement>>() {}.getType());
                    Callable<List<IssueData>> converter = () ->
                            list.parallelStream()
                                    .map(element -> (IssueData) (gson.fromJson(element, finalType)))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                    data = threadPool.submit(converter).join();

                    // freeze the issues
                    threadPool.submit(() -> data.parallelStream().forEach(IssueData::freeze));

                } catch (JsonSyntaxException e) {
                    LOG.warning("Encountered invalid JSON: " + json + "\n\n" + e.getMessage() + "\n\n" + e);
                    return null;
                }
                return data;
            }).ifPresent(list -> {
                Set<IssueData> all = new HashSet<>(list);
                all.addAll(issueProcessor.getCache().values());
                list = new ArrayList<>(all);
                list.sort(Comparator.comparing(issue -> issue.created_at));
                issues = Collections.unmodifiableList(list);
            });
        }

        if (!includePullRequests) {
            return Optional.of(issues.stream().filter(issueData -> !issueData.isPullRequest).collect(Collectors.toList()));
        }
        return Optional.of(issues);
    }

    /**
     * Returns a List of Events for an Issue.
     *
     * @param issue
     *         the parent IssueData
     * @return optionally a List of EventData or an empty Optional if an error occurred
     */
    Optional<List<EventData>> getEvents(IssueData issue) {
        return getJSONStringFromPath("/issues/" + issue.number + "/events").map(json -> {
            try {
                return gson.fromJson(json, new TypeToken<ArrayList<EventData>>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
        });
    }

    /**
     * Returns a List of Comments for an Issue.
     *
     * @param issue
     *         the parent IssueData
     * @return optionally a list of CommentData or an empty Optional if an error occurred
     */
    Optional<List<ReferencedLink<String>>> getComments(IssueData issue) {
        return getJSONStringFromPath("/issues/" + issue.number + "/comments?state=all").map(json -> {
            try {
                return gson.fromJson(json, new TypeToken<ArrayList<ReferencedLink>>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
        });
    }

    /**
     * Returns a List of Reviews for a Pull Request.
     *
     * @param issue
     *         the parent IssueData
     * @return optionally a list of ReviewData or an empty Optional if an error occurred
     */
    Optional<List<ReviewData>> getReviews(IssueData issue) {
        return getJSONStringFromPath("/pulls/" + issue.number + "/reviews?state=all").map(json -> {
            try {
                List<ReviewData> reviews = gson.fromJson(json, new TypeToken<ArrayList<ReviewData>>() {}.getType());

                /* As the reviews extracted from the GitHub API not only contain reviews, but also treats answers
                 * (that is, comments) to reviews as separate reviews, we need to remove those reviews which are just
                 * replies to other reviews.*/
                Optional<List<JsonElement>> reviewComments = getReviewComments(issue);
                List<ReviewData> actualReviews = reviews.stream().filter(review -> {
                    int reviewId = review.getReviewId();

                    List<JsonElement> relatedComments = new ArrayList<JsonElement>();

                    if (reviewComments.isPresent()) {
                        // Get related comments, that is, comments that belong to the review of interest
                        relatedComments = reviewComments.get().stream().filter(reviewComment -> {
                            JsonObject object = gson.fromJson(reviewComment, JsonElement.class).getAsJsonObject();
                            if (!object.get("pull_request_review_id").isJsonNull()) {
                                int refReviewId = object.get("pull_request_review_id").getAsInt();
                                return reviewId == refReviewId;
                            } else {
                                LOG.info("Review comments API for pull request " + issue.number + " not accessible.");
                                return false;
                            }
                        }).collect(Collectors.toList());
                    }

                    if (relatedComments.size() < 1) {
                        // If there are no related comments for the review of interest, it actually is a review and not a comment
                        return true;
                    } else {
                        // As there are related comments, check if there are comments that are *not* a reply.
                        List<JsonElement> nonReplyComments = relatedComments.stream().filter(relatedComment -> {
                            JsonElement inReplyTo = relatedComment.getAsJsonObject().get("in_reply_to_id");
                            return (inReplyTo == null); // return (isNoReply);
                        }).collect(Collectors.toList());
                        // Only if there is, at least, one comment that is not a reply, we actually have found a review
                        return nonReplyComments.size() > 0;
                    }
                }).collect(Collectors.toList());

                // Get the review comments for all actual reviews
                actualReviews = actualReviews.stream().map(review -> {
                    int reviewId = review.getReviewId();

                    // Build a map of comment ids to review ids. This is necessary as the review id of the comment may point to an invalid review.
                    ConcurrentHashMap<Integer,Integer> commentReviewMap = new ConcurrentHashMap<Integer,Integer>();

                    // Get related comments, that is, comments that belong to the review of interest
                    List<JsonElement> relatedComments = new ArrayList<JsonElement>();

                    if (reviewComments.isPresent()) {
                        relatedComments = reviewComments.get().stream().filter(reviewComment -> {
                            JsonObject reviewCommentObject = gson.fromJson(reviewComment, JsonElement.class).getAsJsonObject();
                            Integer refReviewId;
                            if (!reviewCommentObject.get("pull_request_review_id").isJsonNull()) {
                                refReviewId = reviewCommentObject.get("pull_request_review_id").getAsInt();
                            } else {
                                return false;
                            }

                            if (reviewId == refReviewId) {
                                commentReviewMap.put(reviewCommentObject.get("id").getAsInt(), reviewCommentObject.get("pull_request_review_id").getAsInt());
                                return true;
                            } else {
                                JsonElement inReplyTo = reviewCommentObject.get("in_reply_to_id");

                                // If this is not a reply, drop this comment, as the comment does not belong to the review of interest
                                if(inReplyTo == null) {
                                    return false;
                                } else {

                                    // Comment is a reply. Now we need to check whether the referenced comment belongs to the review of interest.
                                    refReviewId = commentReviewMap.get(inReplyTo.getAsInt());

                                    if (refReviewId != null && refReviewId == reviewId) {
                                        commentReviewMap.put(reviewCommentObject.get("id").getAsInt(), refReviewId);
                                        return true;
                                    } else {
                                        return false;
                                    }
                                }
                            }
                        }).collect(Collectors.toList());
                    }

                    List<ReferencedLink<ReviewCommentData>> relatedReviewComments = relatedComments.stream().map(comment -> {
                        String c = gson.toJson(comment);
                        ReferencedLink<ReviewCommentData> r = gson.fromJson(c, new TypeToken<ReferencedLink<ReviewCommentData>>() {}.getType());
                        return r;
                    }).collect(Collectors.toList());

                    review.setReviewComments(Optional.of(relatedReviewComments).orElse(Collections.emptyList()));
                    return review;
                }).collect(Collectors.toList());

                return actualReviews;
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
        });
    }

     /**
     * Returns a List of Comments for all Reviews of a Pull Request.
     *
     * @param issue
     *         the parent IssueData
     * @return optionally a list of JsonElements or an empty Optional if an error occurred
     */
    Optional<List<JsonElement>> getReviewComments(IssueData issue) {
        return getJSONStringFromPath("/pulls/" + issue.number + "/comments?state=all").map(json -> {
            try {
                return gson.fromJson(json, new TypeToken<ArrayList<JsonElement>>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
        });
    }

    /**
     * Gets a List of all Commits before a given Date.
     *
     * @param date
     *         the Date until Commits are included
     * @return optionally a List of Commits or an empty Optional if the operation failed
     */
    public Optional<List<Commit>> getCommitsBeforeDate(Date date) {
        return getCommitsInRange(null, date, "*", false);
    }

    /**
     * Gets a List of all Commits before a given Date.
     *
     * @param date
     *         the Date until Commits are included
     * @param branch
     *         limit Commits to this specific branch
     * @return optionally a List of Commits or an empty Optional if the operation failed
     */
    public Optional<List<Commit>> getCommitsBeforeDate(Date date, String branch) {
        return getCommitsInRange(null, date, branch, false);
    }

    /**
     * Gets a list of merge commits between the two provided times.
     *
     * @param start
     *         the timestamp, after which the first commit is included
     * @param end
     *         the timestamp after the last included commit
     * @return optionally a List of Commits, or an empty Optional if an error occurred
     */
    public Optional<List<Commit>> getMergeCommitsBetween(Date start, Date end) {
        return getCommitsInRange(start, end, "*", true);
    }

    /**
     * Gets a list of merge commits reachable from {@code end} and in the history of {@code start}.
     *
     * @param start
     *         the first commit to include
     * @param end
     *         the last commit to include
     * @return optionally a List of Commits, or an empty Optional if an error occurred
     */
    public Optional<List<Commit>> getMergeCommitsBetween(Commit start, Commit end) {
        return getMergeCommits().map(list -> list.stream()
                .filter(c -> start == null || c.equals(start) || c.checkAncestry(start).orElse(false))
                .filter(c -> end == null || c.equals(end) || end.checkAncestry(c).orElse(true))
                .collect(Collectors.toList()));
    }

    /**
     * Gets the list of pull requests from GitHub, if it is not already cached.
     */
    private void getPullRequests() {
        if (pullRequests != null) {
            LOG.fine("Using cached list of PRs");
            return;
        }
        LOG.fine("Building new list of PRs");
        getIssues(true).ifPresent(issues -> {
            Callable<List<PullRequest>> converter = () -> issues.parallelStream().filter(x -> x.isPullRequest).map(x -> (PullRequestData) x).map(pr -> {
                State state = State.getPRState(pr.state, pr.merged_at != null);

                // if the fork was deleted and the PR was rejected or is still open, we cannot get verify the
                // commits, so the PR is dropped
                if (pr.head.repo == null && State.includes(state, State.UNMERGED)) {
                    LOG.warning(String.format("PR %d has no fork repo and was not merged, therefore it was dropped!", pr.number));
                    return null;
                }

                // if the source branch on the fork was deleted and the PR was declined we also cannot get verify
                // the commits, so the PR is dropped as well
                if (pr.head.repo != null &&
                        !addRemote(pr.head.repo.full_name, pr.head.repo.clone_url) &&
                        !getBranch(pr.head.repo.full_name + "/" + pr.head.ref).isPresent()) {
                    LOG.warning(String.format("The source branch of PR %d was deleted and the PR was not merged, therefore it was dropped!", pr.number));
                    return null;
                }

                // we still can't find the tip, this probably means the history was rewritten and the refs are invalid
                // nothing we can do but drop the PR
                if ( /* TODO super ?*/ !super.getCommit(pr.head.sha).isPresent()) {
                    LOG.warning(String.format("The history of the repo does not include the merged PR %d, therefore it was dropped!", pr.number));
                    return null;
                }

                Reference target = getBranch("origin/" + pr.base.ref).orElse(null);

                Optional<String> commitData = getJSONStringFromPath("/pulls/" + pr.number + "/commits");
                //noinspection unchecked
                List<Commit> commits = commitData.map(cd ->
                        ((ArrayList<RefData>) gson.fromJson(cd, new TypeToken<ArrayList<RefData>>() {}.getType())).stream().map(c ->
                                getCommit(c.sha).orElseGet(() -> {
                                    LOG.warning(String.format("Invalid commit %s from PR %d", c.sha, pr.number));
                                    return null;
                                }))
                            .filter(Objects::nonNull).collect(Collectors.toList()))
                    .orElseGet(() -> {
                        LOG.warning(String.format("Could not get commits for PR %d", pr.number));
                        return Collections.emptyList();
                    });

                if (pr.head.repo == null) {
                    LOG.warning(String.format("PR %d has no fork repo", pr.number));
                    return new PullRequest(this, State.MERGED, target, commits, pr);
                }
                return new PullRequest(this, state, target, commits, pr);
            }).filter(Objects::nonNull).sorted(Comparator.comparing(pr -> pr.getIssue().created_at)).collect(Collectors.toList());
            pullRequests = threadPool.submit(converter).join();
        });
    }

    /**
     * Gets a List of all Commits before a given Date on a branch.
     *
     * @param start
     *         the Date since which Commits are included
     * @param end
     *         the Date until Commits are included
     * @param branch
     *         limit Commits to this specific branch
     * @param onlyMerges
     *         if {@code true} only merge Commits are included
     * @return optionally a List of Commits or an empty optional if the operation failed
     */
    private Optional<List<Commit>> getCommitsInRange(Date start, Date end, String branch, boolean onlyMerges) {
        LOG.fine(String.format("Getting %s between %Tc and %Tc on %s", onlyMerges ? "merges" : "commits", start, end, branch));
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        ArrayList<String> params = new ArrayList<>(Arrays.asList("--format=tformat:%H", "--branches=" + branch));
        if (end != null) params.add("--until=" + df.format(end));
        if (start != null) params.add("--since=" + df.format(start));
        if (onlyMerges) params.add("--merges");
        Optional<ProcessExecutor.ExecRes> commitList = getGit().exec(getDir(), "log", params.toArray(new String[0]));
        Function<ProcessExecutor.ExecRes, List<Commit>> toCommitList = res -> {
            if (getGit().failed(res)) {
                LOG.warning(() -> String.format("Failed to obtain the commits from %s.", this));
                return null;
            }

            if (res.getStdOutTrimmed().isEmpty()) {
                return new ArrayList<>();
            }

            return Arrays.stream(res.getStdOutTrimmed().split("\\s+")).map(this::getCommitUnchecked).collect(Collectors.toList());
        };

        return commitList.map(toCommitList);
    }

    /**
     * Returns a string reading the JSON data returned from the GitHub API called with the API path on the current
     * repository.
     *
     * @param path
     *         the API path to call
     * @return optionally a string representing the JSON result, or an empty Optional, if the call failed
     */
    private Optional<String> getJSONStringFromPath(String path) {
        return getJSONStringFromURL(apiBaseURL + path);
    }

    /**
     * Returns a string of the JSON data returned from the GitHub API called with the given URL.
     * The caller is responsible, that the URL matches this repository.
     *
     * @param urlString
     *         the URL to call
     * @return optionally, a string representing the JSON result, or an empty Optional, if the call failed
     */
    Optional<String> getJSONStringFromURL(String urlString) {
        String json;
        LOG.fine(String.format("Getting json from %s by Thread %s", urlString, Thread.currentThread().getName()));
        try {
            List<String> data = new ArrayList<>();
            String url = urlString + (urlString.contains("?") ? "&" : "?") + "per_page=100";

            Token token = null;
            try {
                Optional<Token> optToken = getValidToken();
                if (!optToken.isPresent()) {
                    LOG.warning("No token available");
                    return Optional.empty();
                }
                token = optToken.get();

                do {
                    if (!token.getToken().isPresent()) {
                        LOG.warning(String.format("Tried to use token %s not held by thread %s", token, Thread.currentThread()));
                        // return Optional.empty();
                    }

                    LOG.info("Querying URL: " + url);
                    HttpGet request = new HttpGet(url);
                    if (!token.getToken().get().isEmpty()) {
                        request.addHeader("Authorization", "token " + token.getToken().get());
                    }
                    CloseableHttpResponse resp = hc.execute(request);

                    Map<String, List<String>> headers = Arrays.stream(resp.getAllHeaders())
                            .collect(Collectors.toMap(Header::getName,
                                    h -> new ArrayList<>(Collections.singletonList(h.getValue())),
                                    (a, b) -> {a.addAll(b); return a;}));

                    int rateLimitRemaining = Integer.parseInt(headers.getOrDefault("X-RateLimit-Remaining", Collections.singletonList("0")).get(0));
                    Instant rateLimitReset = Instant.ofEpochMilli(Long.parseLong(headers.getOrDefault("X-RateLimit-Reset", Collections.singletonList("0")).get(0)) * 1000);
                    token.update(rateLimitRemaining, rateLimitReset);

                    // if this call could have been the last possible, fetch a new token for the next round
                    if (!token.isUsable()) {
                        releaseToken(token);
                        optToken = getValidToken();
                        if (!optToken.isPresent()) {
                            LOG.warning("No token available");
                            token = null;
                            resp.close();
                            return Optional.empty();
                        }
                        token = optToken.get();
                    }

                    if (resp.getStatusLine().getStatusCode() != 200) {
                        LOG.warning(String.format("Could not access api method: %s returned %s", url, resp.getStatusLine()));
                        resp.close();

                        // probably in rate limit, lets try again with the new token
                        if (resp.getStatusLine().getStatusCode() == 403)
                            continue;

                        return Optional.empty();
                    }

                    // read content
                    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(resp.getEntity().getContent()))) {
                        data.add(buffer.lines().collect(Collectors.joining("\n")));
                    }

                    // check, if another page is available
                    Optional<String> next = Arrays.stream(headers.getOrDefault("Link",
                            new ArrayList<>(Collections.singleton(""))
                    ).get(0).split(","))
                            .filter(link -> link.contains("next")).findFirst();
                    resp.close();

                    if (!next.isPresent()) break;
                    String nextUrl = next.get();
                    url = nextUrl.substring(nextUrl.indexOf("<") + 1, nextUrl.indexOf(">"));
                } while (true);

            } finally {
                if (token != null && token.isHeld()) releaseToken(token);
            }

            // concatenate all results together, making one large JSON string
            json = String.join("", data).replace("][", ",");

        } catch (IOException e) {
            LOG.warning(String.format("Could not get data from GitHub (%s), retry...", urlString));

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
            }
            return this.getJSONStringFromURL(urlString);

            //return Optional.empty();
        }

        return json.isEmpty() ? Optional.empty() : Optional.of(json);
    }

    /**
     * Gets a valid API Token.
     * If waiting for the reset of an exhausted token is allowed, this call will block until the first token with free
     * calls is available.
     * This method will {@link Token#acquire() acquire} the lock on the Token, if one is returned, the caller is
     * responsible for releasing the lock, once he does not need it any more.
     *
     * @return a valid token, or an empty Optional if none is found and waiting is not allowed.
     * @see #sleepOnApiLimit(boolean)
     * @see #releaseToken(Token)
     */
    private Optional<Token> getValidToken() {
        synchronized (tokens) {
            // short-circuit if we already have a valid token
            Optional<Token> optToken = tokens.stream().filter(Token::isHeld).filter(Token::isValid).findFirst();
            if (optToken.isPresent()) {
                optToken.get().acquire();
                return optToken;
            }

            // acquire a token or, if allowed wait until one becomes available
            optToken = tokens.stream().filter(Token::isValid).filter(Token::acquire).findFirst();
            if (sleepOnApiLimit() && !optToken.isPresent()) {
                try {
                    LOG.info(String.format("Waiting until %s before the next token is available.", getTokenResetTime()));
                    tokenWaitList.add(Thread.currentThread());
                    Thread.sleep(Math.max(1 , getTokenResetTime().minusMillis(System.currentTimeMillis()).toEpochMilli()));
                } catch (InterruptedException e) {
                    tokenWaitList.removeIf(t -> t.equals(Thread.currentThread()));
                    return getValidToken();
                }
                return getValidToken();
            }
            optToken.ifPresent(token -> LOG.finest(Thread.currentThread() + " acquired token " + token));
            return optToken;
        }
    }

    /**
     * {@link Token#release() Releases} the Token and informs the next one waiting.
     *
     * @param token
     *         the Token to release
     */
    private void releaseToken(Token token) {
        token.release();
        LOG.finest(Thread.currentThread() + " released token " + token);
        Thread next = tokenWaitList.poll();
        if (next != null) {
            LOG.fine("Waking up " + next + " waiting on token");
            next.interrupt();
        }
    }

    /**
     * Gets the earliest time, any of the active tokens can be used again.
     * If there are valid tokens but all are locked, this will return {@code now + 10 seconds}.
     *
     * @return the earliest time a single API call can succeed
     */
    public static Instant getTokenResetTime() {
        synchronized (tokens) {
            if (tokens.stream().anyMatch(Token::isUsable)) return Instant.now();
            if (tokens.stream().anyMatch(Token::isValid))  return Instant.now().plusSeconds(10);
            return tokens.stream().map(Token::getResetTime).min(Comparator.naturalOrder()).orElse(Instant.MAX);
        }
    }

    /**
     * Gets, if the execution is waiting on an API token to becomes available if all tokens are exhausted.
     *
     * @return {@code true} if a sleeping is requested.
     * @see #sleepOnApiLimit(boolean)
     */
    private boolean sleepOnApiLimit() {
        synchronized (sleepOnApiLimit) {
            return sleepOnApiLimit.get();
        }
    }

    /**
     * Setter for toggling waiting on exhausted API rate limit.
     * Default is {@code true}.
     * This is a global switch and takes immediate effect on all running and future requests.
     *
     * @param sleepOnApiLimit
     *         if {@code true}, all API calls will wait until the call blocked due to rate limiting succeeds again
     * @see #getTokenResetTime()
     */
    public void sleepOnApiLimit(boolean sleepOnApiLimit) {
        synchronized (this.sleepOnApiLimit) {
            this.sleepOnApiLimit.set(sleepOnApiLimit);
        }
    }

    /**
     * Gets, if strict email determination is required.
     *
     * @return {@code true} if guessing of user email is allowed
     * @see #allowGuessing(boolean)
     */
    boolean allowGuessing() {
        synchronized (allowGuessing) {
            return allowGuessing.get();
        }
    }

    /**
     * Setter for toggling strict email determination method.
     * Default is {@code false}.
     * This is a global switch and takes immediate effect on all running and future requests.
     *
     * @param guess
     *         if {@code true}, guessing of user email is allowed
     * @see #allowGuessing()
     */
    public void allowGuessing(boolean guess) {
        synchronized (allowGuessing) {
            allowGuessing.set(guess);
        }
    }

    /**
     * This method provides a convenient way to convert GitHub-related objects back to their JSON representation
     * (For now only GitHub related data and commits can be serialized)
     *
     * Notice: This method will end up in an {@code OutOfMemoryError} if the issue data contains a very huge
     * amount of data. To prevent this, use method {@code #streamSerialze(OutputStreamWriter, List<IssueData>)}.
     *
     * @param obj
     *         the object to serialize
     * @return a String containing the JSON representation
     * @see #streamSerialize(OutputStreamWriter, List<IssueData>)
     */
    public <T> String serialize(T obj) {
        return gson.toJson(obj);
    }

    /**
     * This method provides a convenient way to convert a list of {@code IssueData} objects back to their
     * JSON representation and write them directly to an output file using an {@code OutputStreamWriter}.
     * (For now only GitHub related data and commits can be serialized)
     *
     * This method is applicable also for huge amounts of issue data (as opposed to method {@code #serialze(T obj)}),
     * as the complete JSON string is not held in the memory but directly written to a file, using an output stream.
     *
     * @param out
     *         the output stream writer which should be used to write the JSON String representation
     * @param issueData
     *         the list of {@code IssueData} objects to serialize
     * @see #serialize(T)
     */
    public void streamSerialize(OutputStreamWriter out, List<IssueData> issueData) {
        try {
            JsonWriter writer = new JsonWriter(out);
            writer.setIndent("  ");
            writer.beginArray();

            for (IssueData i : issueData) {
                gson.toJson(i, IssueData.class, writer);
                out.flush();
            }
            writer.endArray();
            writer.close();
        } catch (IOException e) {
            LOG.severe("An error occurred during serialization: " + e);
        }
    }

    /**
     * Gets the corresponding Commit for the given sha1 hash. If the commit is not known by the local repository, a
     * query is sent to GitHub, to confirm its existence there and additional author data is retrieved. (e.g. GitHub
     * retains a copy, even if a force push is performed).
     *
     * @param hash
     *         the sha1 hash of the Commit
     * @return optionally a Commit, or an empty Optional, if neither the local repository nor GitHub have a reference
     * to a Commit with the given hash
     */
    Optional<GitHubCommit> getGithubCommit(String hash) {
        return checkedHashes.computeIfAbsent(hash, x -> {
            if (offline.get()) {
                return Optional.of(getGHCommitUnchecked(DummyCommit.DUMMY_COMMIT_ID));
            } else {
                try {
                    return getJSONStringFromURL(apiBaseURL + "/commits/" + hash).map(commitInfo ->
                        gson.fromJson(commitInfo, new TypeToken<GitHubCommit>() {}.getType()));
                } catch (JsonSyntaxException e)  {
                    /* For whatever reason, the JSON String is malformed, perhaps due to ill-encoded characters
                     * in patches within the files element of the JSON String.
                     * Due to that, get the JSON String again and remove the content of the files element of the
                     * JSON String, as it is not needed for further processing.
                     */
                    LOG.info("Malformed JSON String when querying data for commit " + hash + ". Neglect files element.");
                    String jsonStringFromURL = getJSONStringFromURL(apiBaseURL + "/commits/" + hash).get();
                    jsonStringFromURL = StringUtils.substringBefore(jsonStringFromURL, "\"files\":[");
                    jsonStringFromURL = jsonStringFromURL + "\"files\":[]}";
                    return Optional.of(gson.fromJson(jsonStringFromURL, new TypeToken<GitHubCommit>() {}.getType()));
                }
            }
        });
    }

    Optional<GitHubCommit> getGithubCommitUrl(String hash, String url) {
        if (offline.get()) {
            return Optional.of(getGHCommitUnchecked(DummyCommit.DUMMY_COMMIT_ID));
        } else {
            try {
                Optional<GitHubCommit> res = getJSONStringFromURL(url).map(commitInfo ->
                    gson.fromJson(commitInfo, new TypeToken<GitHubCommit>() {}.getType()));
                checkedHashes.put(hash, res);
                if (res.isPresent()) {
                    res.get().setExternal(true);
                }
                return res;
            } catch (JsonSyntaxException e)  {
                /* For whatever reason, the JSON String is malformed, perhaps due to ill-encoded characters
                 * in patches within the files element of the JSON String.
                 * Due to that, get the JSON String again and remove the content of the files element of the
                 * JSON String, as it is not needed for further processing.
                 */
                LOG.info("Malformed JSON String when querying data for commit " + url + ". Neglect files element.");
                String jsonStringFromURL = getJSONStringFromURL(url).get();
                jsonStringFromURL = StringUtils.substringBefore(jsonStringFromURL, "\"files\":[");
                jsonStringFromURL = jsonStringFromURL + "\"files\":[]}";
                Optional<GitHubCommit> res = Optional.of(gson.fromJson(jsonStringFromURL, new TypeToken<GitHubCommit>() {}.getType()));
                checkedHashes.put(hash, res);
                if (res.isPresent()) {
                    res.get().setExternal(true);
                }
                return res;
            }
        }
    }

    /**
     * Creates a new Commit with the given data, and tries to fill in the  missing data from the local Repository
     *
     * @param hash
     *         the sha1 hash of the Commit
     * @param message
     *         the commit messages
     * @param author
     *         Data about the Commit author
     * @return the new Commit
     */
    GitHubCommit getReferencedCommit(String hash, String message, UserData.CommitUserData author) {
        return getReferencedCommit(hash, message, author, null);
    }

    /**
     * Creates a new Commit with the given data, and tries to fill in the  missing data from the local Repository
     *
     * @param hash
     *         the sha1 hash of the Commit
     * @param message
     *         the commit messages
     * @param author
     *         Data about the Commit author
     * @param committer
     *         Data about the Commit committer
     * @return the new Commit
     */
    GitHubCommit getReferencedCommit(String hash, String message, UserData.CommitUserData author, UserData.CommitUserData committer) {
        // Disable logging for this method call, so false positives don't reported
        Logger repoLog = Logger.getLogger(Repository.class.getCanonicalName());
        Logger commitLog = Logger.getLogger(Commit.class.getCanonicalName());
        Level repoLevel = repoLog.getLevel();
        Level commitLevel = commitLog.getLevel();
        repoLog.setLevel(Level.OFF);
        commitLog.setLevel(Level.OFF);

        GitHubCommit commit = getGHCommitUnchecked(hash);

        commit.setAuthor(author.name);
        commit.setAuthorMail(author.email);
        commit.setAuthorTime(author.date);
        commit.setMessage(message);
        commit.setAuthorUsername(author.githubUsername);

        if(committer != null) {
            commit.setCommitter(committer.name);
            commit.setCommitterMail(committer.email);
            commit.setCommitterTime(committer.date);
            commit.setCommitterUsername(committer.githubUsername);
        }

        // Reset logging
        repoLog.setLevel(repoLevel);
        commitLog.setLevel(commitLevel);

        return commit;
    }

    /**
     * Returns a {@link GitHubCommit} for the given ID. The caller must ensure that the ID is a full SHA1 hash of a
     * commit that exists in this repository.
     *
     * @param id
     *         the ID for the {@link Commit}
     * @return the {@link GitHubCommit}
     * @see #getCommitUnchecked(String)
     */
    GitHubCommit getGHCommitUnchecked(String id) {
        return getGHCommit(id).orElse(unknownCommits.computeIfAbsent(id, (key) -> new GitHubCommit(this, key)));
    }

     /**
     * Optionally returns a {@link GitHubCommit} for the given ID. If the given ID does not designate a commit that exists
     * in this {@link Repository}, an empty {@link Optional} will be returned. The ID will be resolved to a full SHA1 hash.
     *
     * @param id
     *         the ID of the commit
     * @return the {@link GitHubCommit} or an empty {@link Optional} if the ID is invalid or an exception occurs
     */
    public Optional<GitHubCommit> getGHCommit(String id) {
        Optional<Commit> commit = Optional.ofNullable(super.getCommit(id).orElse(unknownCommits.get(id)));
        Optional<GitHubCommit> ghCommit = Optional.empty();
        if(commit.isPresent()) {
            ghCommit = Optional.ofNullable(new GitHubCommit(commit.get(), this, commit.get().getId()));
        }
        return ghCommit;
    }

    @Override
    Commit getCommitUnchecked(String id) {
        return getCommit(id).orElse(unknownCommits.computeIfAbsent(id, (key) -> new GitHubCommit(this, key)));
    }

    @Override
    public Optional<Commit> getCommit(String id) {
        return Optional.ofNullable(super.getCommit(id).orElse(unknownCommits.get(id)));
    }

    /**
     * Gets a PullRequest by its full qualified branch name.
     *
     * @param name
     *         the branch name
     * @return optionally the PullRequest, if it is known
     */
    public Optional<PullRequest> getPullRequest(String name) {
        if (pullRequests != null) {
            return pullRequests.stream().filter(pr -> ((PullRequestData) pr.getIssue()).branch.equals(name)).findFirst();
        } else {
            return issues.stream().filter(IssueData::isPullRequest).map(pr -> ((PullRequestData) pr))
                    .filter(pr -> pr.branch.equals(name)).findFirst().map(prd ->
                            new PullRequest(this, State.getPRState(prd.state, prd.merged_at != null),
                                    getBranch("origin/" + prd.base.ref).orElse(null), /*TODO*/ null, prd));
        }
    }

    IssueData getIssueFromCache(Integer target) {
        return issueProcessor.getCache().get(target);
    }

    /**
     * Only used internally for deserialization, don't implement!
     */
    interface IssueDataCached {}
}
