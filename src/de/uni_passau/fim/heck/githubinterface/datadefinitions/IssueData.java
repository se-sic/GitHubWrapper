package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.uni_passau.fim.heck.githubinterface.GitHubRepository;
import de.uni_passau.fim.heck.githubinterface.IssueDataPostprocessor;
import de.uni_passau.fim.heck.githubinterface.PullRequest;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;

import java.util.*;
import javax.annotation.Nullable;

/**
 * Data object for information about Issues.
 */
public class IssueData implements GitHubRepository.IssueDataCached {

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
    public boolean isPullRequest = false;

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

    private List<CommentData> commentsList;
    private List<EventData> eventsList;
    private List<Commit> relatedCommits;
    // we serialize this list manually, since it may contain circles and even if not adds a lot of repetitive data
    private transient List<IssueData> relatedIssues = new ArrayList<>();

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
     * Adds a related Issue to this Issue.
     *
     * @param issues
     *         the issue.
     * @see IssueDataPostprocessor#parseIssues(IssueData, Gson)
     */
    public void addRelatedIssues(List<IssueData> issues) {
        issues.sort(Comparator.comparing(issue -> issue.created_at));
        relatedIssues = Collections.unmodifiableList(issues);
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

    /**
     * Gets a List of all Issues referenced in the Issue and its Comments.
     *
     * @return a List of IssueData
     */
    public List<IssueData> getRelatedIssues() {
        return relatedIssues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IssueData)) return false;
        IssueData issueData = (IssueData) o;
        return Objects.equals(url, issueData.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
