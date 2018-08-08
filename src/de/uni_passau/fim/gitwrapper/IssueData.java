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

    private List<ReferencedLink<String>> commentsList;
    private List<EventData> eventsList;
    private List<ReferencedLink<Commit>> relatedCommits;
    List<ReferencedLink<Integer>> relatedIssues;

    // we serialize this list manually, since it may contain circles and even if not adds a lot of repetitive data
    private transient List<ReferencedLink<IssueData>> relatedIssuesList = new ArrayList<>();

    transient GitHubRepository repo;
    private transient boolean frozen;

    IssueData() { }

    /**
     * Adds a list of Comments to this Issue.
     *
     * @param comments
     *         the Comment list
     */
    public void addComments(List<ReferencedLink<String>> comments) {
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
    public void addRelatedCommits(List<ReferencedLink<Commit>> commits) {
        relatedCommits = commits;
    }

    /**
     * Adds a related Issue to this Issue.
     *
     * @param issues
     *         the issue.
     * @see IssueDataProcessor#parseIssues(IssueData, Gson)
     */
    public void addRelatedIssues(List<ReferencedLink<IssueData>> issues) {
        relatedIssues = issues.stream().map(issue ->
                new ReferencedLink<>(issue.target.number, issue.user, issue.referenced_at)
        ).collect(Collectors.toList());
        relatedIssuesList = issues;
    }

    /**
     * Gets a List of all comments.
     *
     * @return a List of comments in form of ReferencedLink<String>
     */
    public List<ReferencedLink<String>> getCommentsList() {
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
    public List<ReferencedLink<Commit>> getRelatedCommits() {
        return relatedCommits;
    }

    /**
     * Gets a List of all Issues referenced in the Issue and its Comments.
     *
     * @return a List of IssueData
     */
    public List<ReferencedLink<IssueData>> getRelatedIssues() {
        return relatedIssuesList;
    }

    /**
     * Before accessing data for the first time, init, sort and lock all data once.
     */
    void freeze() {
        if (frozen) return;

        eventsList = Collections.unmodifiableList(eventsList.stream()
                .sorted(Comparator.comparing(link -> link.created_at)).collect(Collectors.toList()));
        commentsList = Collections.unmodifiableList(commentsList.stream()
                .sorted(Comparator.comparing(link -> link.referenced_at)).collect(Collectors.toList()));
        if (relatedIssuesList == null) {
            relatedIssuesList = relatedIssues.stream().map(id ->
                    new ReferencedLink<>(repo.getIssueFromCache(id.target), id.user, id.referenced_at)
            ).collect(Collectors.toList());
        }
        relatedIssuesList = Collections.unmodifiableList(relatedIssuesList.stream()
                .sorted(Comparator.comparing(link -> link.referenced_at)).collect(Collectors.toList()));
        relatedCommits = Collections.unmodifiableList(relatedCommits.stream()
                // Remove invalid commits before they cause problems
                .filter(c -> c.getTarget() != null && c.getTarget().getAuthorTime() != null)
                .sorted(Comparator.comparing(link -> link.referenced_at)).collect(Collectors.toList()));

        frozen = true;
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
}
