package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.heck.githubinterface.GitHubRepository.IssueDataCached;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.*;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;
import de.uni_passau.fim.seibt.gitwrapper.repo.LocalRepository;
import io.gsonfire.PostProcessor;

import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The IssueDataPostprocessor extracts additional information from issues and populated IssueData instances with other
 * git-related information.
 */
public class IssueDataPostprocessor implements JsonDeserializer<IssueDataCached>, PostProcessor<IssueData> {
    private static Map<String, IssueData> cache = new HashMap<>();
    private static Map<String, IssueData> workingQueue = new HashMap<>();

    private final GitHubRepository repo;
    private final String issueBaseUrl;
    private final JsonParser parser = new JsonParser();

    /**
     * Creates a new IssueDataPostprocessor for handling Issues specific to the provided GitHubRepository.
     *
     * @param repo
     *         the repository
     */
    IssueDataPostprocessor(GitHubRepository repo, String issueBaseUrl) {
        this.repo = repo;
        this.issueBaseUrl = issueBaseUrl;
    }

    @Override
    public void postDeserialize(IssueData result, JsonElement src, Gson gson) {
        // check if currently worked on, to break cyclic dependencies
        IssueData workingOn = workingQueue.get(result.url);
        if (workingOn != null) return;
        workingQueue.put(result.url, result);
        cache.put(result.url, result);

        result.state = State.getFromString(src.getAsJsonObject().get("state").getAsString());

        IssueData lookup = result;
        JsonElement issueSource = src;
        if (result.isPullRequest) {
            issueSource =  parser.parse(repo.getJSONStringFromURL(src.getAsJsonObject().get("issue_url").getAsString())
                    .orElseThrow(() -> new JsonParseException("Could not get Issue data for this PR: " + result.url)));
            lookup = gson.fromJson(issueSource, new TypeToken<IssueDataCached>() {}.getType());
        }

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

        workingQueue.remove(result.url);
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
                .map(eventData -> ((EventData.ReferencedEventData) eventData).commit.getId());

        return Stream.concat(Stream.concat(commentCommits, referencedCommits), extractSHA1s(issue.body).stream())
                .map(hash -> {
                    // Disable logging for this method call, so false positives don't reported
                    Logger log = Logger.getLogger(LocalRepository.class.getCanonicalName());
                    Level level = log.getLevel();
                    log.setLevel(Level.OFF);
                    Optional<Commit> c = repo.getCommit(hash);
                    log.setLevel(level);

                    return c;
                })

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
    List<IssueData> parseIssues(IssueData issue, Gson gson) {
        Stream<String> commentIssues = issue.getCommentsList().stream().map(comment -> comment.body)
                .flatMap(body -> extractHashtags(body).stream());

        // to get real reference events, the timeline api needs to be incorporated. Since it is still in preview as of
        // 2018-04, I have not implemented it.
        // For details and links: https://gist.github.com/dahlbyk/229f6ee762e2b0b45f3add7c2459e64a

        return Stream.concat(commentIssues, extractHashtags(issue.body).stream()).map(
                link -> {
                    Optional<String> refIssue = repo.getJSONStringFromURL(issueBaseUrl + link);
                    return refIssue.map(s -> (IssueData) gson.fromJson(s, new TypeToken<IssueDataCached>() {}.getType()));
                })
                // filter out false positive matches on normal words (and other errors)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
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
    public void postSerialize(JsonElement result, IssueData src, Gson gson) {
        JsonArray issues = new JsonArray();
        src.getRelatedIssues().stream().map(issue -> issue.number).forEach(issues::add);
        result.getAsJsonObject().add("relatedIssues", issues);
    }

    @Override
    public IssueDataCached deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonElement url = json.getAsJsonObject().get("html_url");
        if (url == null) {
            url = json.getAsJsonObject().get("url");
        }
        IssueData cached = cache.get(url.getAsString());
        if (cached != null) {
            return cached;
        }

        JsonElement pr = json.getAsJsonObject().get("pull_request");
        if(pr != null) {
            JsonElement finalUrl = url;
            String data = repo.getJSONStringFromURL(pr.getAsJsonObject().get("url").getAsString())
                    .orElseThrow(() -> new JsonParseException("Could not get PullRequest data for this issue: " + finalUrl.toString()));
            return context.deserialize(parser.parse(data), new TypeToken<PullRequestData>() {}.getType());
        }

        pr = json.getAsJsonObject().get("isPullRequest");
        if (pr != null && pr.getAsBoolean()) {
            return context.deserialize(json, new TypeToken<PullRequestData>() {}.getType());
        }

        return context.deserialize(json, new TypeToken<IssueData>() {}.getType());
    }

    static void addCache(List<IssueData> protoCache) {
        cache.putAll(protoCache.stream().collect(Collectors.toMap(issue -> issue.url, issue -> issue)));
    }
}
