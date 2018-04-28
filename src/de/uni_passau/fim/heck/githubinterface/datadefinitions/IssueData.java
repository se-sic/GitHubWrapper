package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.uni_passau.fim.heck.githubinterface.IssueDataPostprocessor;
import de.uni_passau.fim.heck.githubinterface.PullRequest;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;

import java.util.*;
import javax.annotation.Nullable;

/**
 * Data object for information about Issues.
 */
public class IssueData {

    /**
     * The number of the issue (referenced with {@code #nr}).
     */
    public int number;

    /**
     * Data about the User that created the issue.
     */
    public UserData user;

    /**
     * Information about the state of the issue.
     */
    @Expose(deserialize = false)
    public State state;

    /**
     * The date and time the issue was created.
     */
    public Date created_at;

    /**
     * The date and time the issue was closed, or null if it is still open.
     */
    @Nullable
    public Date closed_at;

    /**
     * {@code true} if it is a PullRequest.
     *
     * @see PullRequestData
     * @see PullRequest
     */
    public boolean isPullRequest;

    /**
     * the text title.
     */
    public String title;

    /**
     * The text body.
     */
    public String body;

    /**
     * The HTML URL to this issue.
     */
    @SerializedName(value = "url", alternate = {"html_url"})
    public String url;

    private List<CommentData> commentsList = new ArrayList<>();
    private List<EventData> eventsList = new ArrayList<>();
    private List<Commit> relatedCommits = new ArrayList<>();

    /**
     * Adds a list of Comments to this Issue.
     *
     * @param comments
     *         the Comment list
     */
    public void addComments(List<CommentData> comments) {
        comments.sort(Comparator.comparing((comment) -> comment.created_at));
        commentsList = Collections.unmodifiableList(comments);
    }

    /**
     * Adds a list of Events to this Issue.
     *
     * @param events
     *         the Event list.
     */
    public void addEvents(List<EventData> events) {
        events.sort(Comparator.comparing(event -> event.created_at));
        eventsList = Collections.unmodifiableList(events);
    }

    /**
     * Adds a related Commit to this Issue.
     *
     * @param commits
     *         the Commit list
     * @see IssueDataPostprocessor#parseCommits(IssueData)
     */
    public void addRelatedCommits(List<Commit> commits) {
        commits.sort(Comparator.comparing(Commit::getAuthorTime));
        relatedCommits = Collections.unmodifiableList(commits);
    }

    /**
     * Gets a List of all Comments
     *
     * @return a List of CommentData
     */
    public List<CommentData> getCommentsList() {
        return commentsList;
    }

    /**
     * Gets a List all Events.
     *
     * @return a List of EventData
     */
    public List<EventData> getEventsList() {
        return eventsList;
    }

    /**
     * Gets a List of all Commits referenced in the Issue, its Comments and Events.
     *
     * @return a List of Commits
     */
    public List<Commit> getRelatedCommits() {
        return relatedCommits;
    }
}
