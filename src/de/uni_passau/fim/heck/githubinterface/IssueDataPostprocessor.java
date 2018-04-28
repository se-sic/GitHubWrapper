package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.CommentData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.IssueData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.State;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;
import de.uni_passau.fim.seibt.gitwrapper.repo.LocalRepository;
import io.gsonfire.PostProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
public class IssueDataPostprocessor implements PostProcessor<IssueData> {

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
        result.isPullRequest = src.getAsJsonObject().get("pull_request") != null;
        result.state = State.getFromString(src.getAsJsonObject().get("state").getAsString());

        Optional<List<CommentData>> comments = repo.getComments(result);
        Optional<List<EventData>> events = repo.getEvents(result);

        comments.ifPresent(result::addComments);
        events.ifPresent(result::addEvents);

        result.addRelatedCommits(parseCommits(result));
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
     * Extracts theoretically valid commit hashes form text.
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

    @Override
    public void postSerialize(JsonElement result, IssueData src, Gson gson) { }
}
