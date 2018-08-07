package de.uni_passau.fim.gitwrapper;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Data object for information about Issues.
 */
public class IssueData implements GitHubRepository.IssueDataCached {

    int number;
    String title;
    String body;
    UserData user;

    @Expose(deserialize = false) State state;
    OffsetDateTime created_at;
    @Nullable OffsetDateTime closed_at;

    boolean isPullRequest = false;

    @SerializedName(value = "url", alternate = {"html_url"}) String url;

    private List<CommentData> commentsList;
    private List<EventData> eventsList;
    private List<Commit> relatedCommits;
    // we serialize this list manually, since it may contain circles and even if not adds a lot of repetitive data
    private List<ReferencedIssueData> relatedIssues = new ArrayList<>();

    private transient boolean frozen;

    /**
     * Adds a list of Comments to this Issue.
     *
     * @param comments
     *         the Comment list
     */
    public void addComments(List<CommentData> comments) {
        commentsList = comments;
    }

    /**
     * Adds a list of Events to this Issue.
     *
     * @param events
     *         the Event list.
     */
    public void addEvents(List<EventData> events) {
        eventsList = events;
    }

    /**
     * Adds a related Commit to this Issue.
     *
     * @param commits
     *         the Commit list
     * @see IssueDataProcessor#parseCommits(IssueData)
     */
    public void addRelatedCommits(List<Commit> commits) {
        relatedCommits = commits;
    }

    /**
     * Adds a related Issue to this Issue.
     *
     * @param issues
     *         the issue.
     * @see IssueDataProcessor#parseIssues(IssueData, Gson)
     */
    public void addRelatedIssues(List<ReferencedIssueData> issues) {
        relatedIssues = issues;
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
    public List<ReferencedIssueData> getRelatedIssues() {
        return relatedIssues;
    }

    /**
     * Before accessing data for the first time, sort and lock all data once.
     */
    public void freeze() {
        if (frozen) return;

        eventsList = Collections.unmodifiableList(eventsList.stream()
                .sorted(Comparator.comparing(event -> event.created_at)).collect(Collectors.toList()));
        commentsList = Collections.unmodifiableList(commentsList.stream()
                .sorted(Comparator.comparing((comment) -> comment.created_at)).collect(Collectors.toList()));
        relatedIssues = Collections.unmodifiableList(relatedIssues.stream()
                .sorted(Comparator.comparing(issue -> issue.issue.created_at)).collect(Collectors.toList()));
        relatedCommits = Collections.unmodifiableList(relatedCommits.stream()
                // Remove invalid commits before they cause problems
                .filter(c -> c.getAuthorTime() != null)
                .sorted(Comparator.comparing(Commit::getAuthorTime)).collect(Collectors.toList()));
    }

    /**
     * The number of the issue (referenced with {@code #nr}).
     */
    public int getNumber() {
        return number;
    }

    /**
     * Data about the User that created the issue.
     */
    public UserData getUser() {
        return user;
    }

    /**
     * Information about the state of the issue.
     */
    public State getState() {
        return state;
    }

    /**
     * The date and time the issue was created.
     */
    public OffsetDateTime getCreateDate() {
        return created_at;
    }

    /**
     * The date and time the issue was closed, or null if it is still open.
     */
    @Nullable
    public OffsetDateTime getCloseDate() {
        return closed_at;
    }

    /**
     * {@code true} if it is a PullRequest.
     *
     * @see PullRequestData
     * @see PullRequest
     */
    public boolean isPullRequest() {
        return isPullRequest;
    }

    /**
     * the text title.
     */
    public String getTitle() {
        return title;
    }

    /**
     * The text body.
     */
    public String getBody() {
        return body;
    }

    /**
     * The HTML URL to this issue.
     */
    public String getUrl() {
        return url;
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

    /**
     * Wrapper for additional information about referenced issues.
     */
    public static class ReferencedIssueData {
        private transient IssueData issue;
        private UserData user;
        private OffsetDateTime referenced_at;

        /**
         * Creates a new ReferencedIssueData wrapper for adding additional information to linked issues.
         *
         * @param issue
         *         the referenced Issue
         * @param user
         *         the referencing user
         * @param referencedTime
         *         the referencing time
         */
        ReferencedIssueData(IssueData issue, UserData user, OffsetDateTime referencedTime) {
            this.issue = issue;
            this.user = user;
            this.referenced_at = referencedTime;
        }

        /**
         * Gets the issue that was referenced.
         *
         * @return the referenced Issue
         */
        public IssueData getIssue() {
            return issue;
        }

        /**
         * Gets the user has written the comment that referenced the issue.
         *
         * @return the referencing user
         */
        public UserData getUser() {
            return user;
        }

        /**
         * Gets the time the comment that referenced the issue was written.
         *
         * @return the referencing time
         */
        public OffsetDateTime getReferencedTime() {
            return referenced_at;
        }
    }
}
