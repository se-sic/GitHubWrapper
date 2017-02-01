package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import com.sun.istack.internal.Nullable;
import de.uni_passau.fim.heck.githubinterface.IssueDataPostprocessor;
import de.uni_passau.fim.heck.githubinterface.PullRequest;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;

/**
 * Data object for information about Issues.
 */
public class IssueData {

    /**
     * The number of the issue (referenced with {@code #nr})
     */
    public int number;

    /**
     * Data about the User that created the issue.
     */
    public UserData user;

    /**
     * Information about the state of the issue.
     */
    public String state;

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
     * {@code true} if it is a PullRequest
     *
     * @see PullRequestData
     * @see PullRequest
     */
    public boolean isPullRequest;

    /**
     * The text body.
     */
    public String body;

    @SerializedName(value = "url", alternate = {"html_url"})
    public String url;

    private List<CommentData> commentsList = new ArrayList<>();
    private List<EventData> eventsList = new ArrayList<>();
    private List<Commit> relatedCommits = new ArrayList<>();

    /**
     * Adds a Comment to this Issue.
     *
     * @param comment
     *         the Comment
     */
    public void addComment(CommentData comment) {
        commentsList.add(comment);
    }

    /**
     * Adds an Event to this Issue.
     *
     * @param event
     *         the Event.
     */
    public void addEvent(EventData event) {
        eventsList.add(event);
    }

    /**
     * Adds a related Commit to this Issue.
     *
     * @param commit
     *         the Commit.
     * @see IssueDataPostprocessor#parseCommits(IssueData)
     */
    public void addRelatedCommit(Commit commit) {
        relatedCommits.add(commit);
    }

    /**
     * Gets a List of all Comments
     *
     * @return a List of CommentData
     */
    public List<CommentData> getCommentsList() {
        return Collections.unmodifiableList(commentsList);
    }

    /**
     * Gets a List all Events.
     *
     * @return a List of EventData
     */
    public List<EventData> getEventsList() {
        return Collections.unmodifiableList(eventsList);
    }

    /**
     * Gets a List of all Commits referenced in the Issue, its Comments and Events.
     *
     * @return a List of Commits
     */
    public List<Commit> getRelatedCommits() {
        return Collections.unmodifiableList(relatedCommits);
    }
}
