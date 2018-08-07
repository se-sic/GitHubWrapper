package de.uni_passau.fim.gitwrapper;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.gitwrapper.GitHubRepository.IssueDataCached;
import io.gsonfire.PostProcessor;

import java.lang.reflect.Type;
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
     * Creates a new IssueDataPostprocessor for handling Issues specific to the provided GitHubRepository.
     *
     * @param repo
     *         the repository
     */
    IssueDataProcessor(GitHubRepository repo, String issueBaseUrl) {
        this.repo = repo;
        this.issueBaseUrl = issueBaseUrl;
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
            // we need the actual issue data back, because events and comments are missing in an pr
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

        Optional<List<CommentData>> comments = repo.getComments(lookup);
        Optional<List<EventData>> events = repo.getEvents(lookup);

        comments.ifPresent(result::addComments);
        events.ifPresent(result::addEvents);

        List<Commit> commits = parseCommits(result);
        if (result.isPullRequest) {
            Optional<String> json = repo.getJSONStringFromURL(src.getAsJsonObject().get("commits_url").getAsString());
            json.ifPresent(data -> commits.addAll(gson.fromJson(data, new TypeToken<ArrayList<Commit>>(){}.getType())));
        }

        result.addRelatedCommits(commits);
        result.addRelatedIssues(parseIssues(result, gson));

        workingQueue.remove(result.number);
    }

    /**
     * Parses Commits from issue body, comment bodies and referenced events.
     *
     * @param issue
     *         the IssueData
     * @return a List of all referenced Commits
     */
    private List<Commit> parseCommits(IssueData issue) {
        Stream<String> commentCommits = issue.getCommentsList().stream().map(comment -> comment.body)
                .flatMap(body -> extractSHA1s(body).stream());

        Stream<String> referencedCommits = issue.getEventsList().stream()
                .filter(eventData -> eventData instanceof EventData.ReferencedEventData)
                // filter out errors from referencing commits
                .filter(eventData -> ((EventData.ReferencedEventData) eventData).commit != null)
                .map(eventData -> ((EventData.ReferencedEventData) eventData).commit.getId());

        return Stream.concat(Stream.concat(commentCommits, referencedCommits), extractSHA1s(issue.body).stream())
                .map(repo::getGithubCommit)

                // filter out false positive matches on normal words (and other errors)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Parses a list of all referenced Issues
     *
     * @param issue
     *         the Issue to analyze
     * @param gson
     *         the Gson used to deserialize
     * @return a list of all Issues that are referenced in the body and the comments
     */
    List<IssueData.ReferencedIssueData> parseIssues(IssueData issue, Gson gson) {
        Stream<ReferencedIssueHandler> commentIssues = issue.getCommentsList().stream().map(comment ->
                new ReferencedIssueHandler(comment, extractHashtags(comment.body)));

        // to get real reference events, the timeline api needs to be incorporated. Since it is still in preview as of
        // 2018-04, I have not implemented it.
        // For details and links: https://gist.github.com/dahlbyk/229f6ee762e2b0b45f3add7c2459e64a

        CommentData temp = new CommentData();
        temp.created_at = issue.created_at;
        temp.user = issue.user;
        return Stream.concat(commentIssues, Stream.of(new ReferencedIssueHandler(temp, extractHashtags(issue.body)))).flatMap(
                commentEntries -> commentEntries.links.stream().map(link -> {
                    int num;
                    try {
                        num = Integer.parseInt(link);
                    } catch (NumberFormatException e) {
                        //noinspection ConstantConditions Reason: type inference
                        return Optional.ofNullable((IssueData) null);
                    }

                    // again, short-circuit, if he have a cache hit
                    IssueData cached = cache.get(num);
                    if (cached != null) {
                        return Optional.of(cached);
                    }

                    Optional<String> refIssue = repo.getJSONStringFromURL(issueBaseUrl + link);
                    return refIssue.map(s -> (IssueData) gson.fromJson(s, new TypeToken<IssueDataCached>() {}.getType()));
                })
                // filter out false positive matches on normal words (and other errors)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .map(target -> new IssueData.ReferencedIssueData(target, commentEntries.comment.user, commentEntries.comment.created_at))
        ).collect(Collectors.toList());
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
     * @return a List of all valid hashtags
     */
    private List<String> extractHashtags(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        Pattern sha1Pattern = Pattern.compile("#([0-9]{1,11})");
        Matcher matcher = sha1Pattern.matcher(text);

        List<String> hashtags = new ArrayList<>();

        while (matcher.find()) {
            hashtags.add(matcher.group(1));
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
            cached.body = json.getAsJsonObject().get("body").getAsString();
            cached.title = json.getAsJsonObject().get("title").getAsString();
            return cached;
        }

        // check if pr directly from github
        JsonElement pr = json.getAsJsonObject().get("pull_request");
        if(pr != null) {
            // get additional data
            String data = repo.getJSONStringFromURL(pr.getAsJsonObject().get("url").getAsString())
                    .orElseThrow(() -> new JsonParseException("Could not get PullRequest data for issue: " + number + " in repo " + repo));
            return context.deserialize(parser.parse(data), new TypeToken<PullRequestData>() {}.getType());
        }

        // check if pr from local dump, we then already have all data
        pr = json.getAsJsonObject().get("isPullRequest");
        if (pr != null && pr.getAsBoolean()) {
            return context.deserialize(json, new TypeToken<PullRequestData>() {}.getType());
        }

        // normal issue from github or dump
        return context.deserialize(json, new TypeToken<IssueData>() {}.getType());
    }

    /**
     * Manually adds Issues to the cache used for short-circuiting deserialization.
     *
     * @param protoCache
     *         a list of prototype IssueData elements ({@link IssueData#commentsList}, {@link IssueData#eventsList},
     *         {@link IssueData#relatedCommits} and {@link IssueData#relatedIssues} are allowed to be missing)
     */
    void addCache(List<IssueData> protoCache) {
        cache.putAll(protoCache.stream().collect(Collectors.toMap(issue -> issue.number, issue -> issue)));
    }

    /**
     * Gets a list of all cached issues.
     *
     * @return a collection of all cached issues
     */
    Collection<IssueData> getCache() {
        return cache.values();
    }

    /**
     * The ReferencedIssueHandler handles (de-)serialization of links to other issues. Further, this is used as
     * temporary wrapper for relating links to their source comment.
     */
    static class ReferencedIssueHandler implements PostProcessor<IssueData.ReferencedIssueData> {
        private CommentData comment;
        private List<String> links;

        /**
         * Constructor for use as temporary "Pair" replacement.
         *
         * @param comment
         *         the source comment
         * @param links
         *         the list of linked issue number
         */
        private ReferencedIssueHandler(CommentData comment, List<String> links) {
            this.comment = comment;
            this.links = links;
        }

        /**
         * ReferencedIssueHandler is used for post processing related issue lists to map the issues to their numbers.
         */
        ReferencedIssueHandler() { }

        @Override
        public void postDeserialize(IssueData.ReferencedIssueData result, JsonElement src, Gson gson) { }

        @Override
        public void postSerialize(JsonElement result, IssueData.ReferencedIssueData src, Gson gson) {
            result.getAsJsonObject().addProperty("number", src.getIssue().number);
        }
    }
}
