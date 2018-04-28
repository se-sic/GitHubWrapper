package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.CommentData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.IssueData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.State;
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
public class IssueDataPostprocessor implements JsonDeserializer<GitHubRepository.IssueDataCached>, PostProcessor<IssueData> {
    private static Map<String, IssueData> cache = new HashMap<>();
    private static Map<String, IssueData> workingQueue = new HashMap<>();

    private final GitHubRepository repo;

    /**
     * Creates a new IssueDataPostprocessor for handling Issues specific to the provided GitHubRepository.
     *
     * @param repo
     *         the repository
     */
    IssueDataPostprocessor(GitHubRepository repo) {
        this.repo = repo;
    }

    @Override
    public void postDeserialize(IssueData result, JsonElement src, Gson gson) {
        // check if currently worked on, to break cyclic dependencies
        IssueData cacheCheck = workingQueue.get(result.url);
        if (cacheCheck != null) return;
        workingQueue.put(result.url, result);
        cache.put(result.url, result);

        result.isPullRequest = src.getAsJsonObject().get("pull_request") != null;
        result.state = State.getFromString(src.getAsJsonObject().get("state").getAsString());

        Optional<List<CommentData>> comments = repo.getComments(result);
        Optional<List<EventData>> events = repo.getEvents(result);

        comments.ifPresent(list -> list.forEach(result::addComment));
        events.ifPresent(list -> list.forEach(result::addEvent));

        parseCommits(result).forEach(result::addRelatedCommit);
        parseIssues(result, src, gson).forEach(result::addRelatedIssue);

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
                .map(eventData -> ((EventData.ReferencedEventData) eventData).commit_id);

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
     *
     * @param issue
     * @param src
     * @param gson
     * @return
     */
    private List<IssueData> parseIssues(IssueData issue, JsonElement src, Gson gson) {
        Stream<String> commentIssues = issue.getCommentsList().stream().map(comment -> comment.body)
                .flatMap(body -> extractHashtags(body).stream());

        // to get real reference events, the timeline api needs to be incorporated. Since it is still in preview as of
        // 2018-04, I have not implemented it.
        // For details and links: https://gist.github.com/dahlbyk/229f6ee762e2b0b45f3add7c2459e64a

        return Stream.concat(commentIssues, extractHashtags(issue.body).stream()).map(
                link -> {
                    Optional<String> refIssue = repo.getJSONStringFromURL(src.getAsJsonObject().get("url").getAsString().replaceAll("/\\d+$", "/" + link));
                    return refIssue.map(s -> (IssueData) gson.fromJson(s, new TypeToken<GitHubRepository.IssueDataCached>() {}.getType()));
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
    public void postSerialize(JsonElement result, IssueData src, Gson gson) { }

    @Override
    public GitHubRepository.IssueDataCached deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        IssueData cached = cache.get(json.getAsJsonObject().get("html_url").getAsString());
        if (cached != null) {
            return cached;
        }
        return context.deserialize(json, new TypeToken<IssueData>() {}.getType());
    }


}
