/**
 * Copyright (C) 2016-2018 Florian Heck
 * Copyright (C) 2019 Thomas Bock
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
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data object for information about Issues.
 */
public class IssueData implements GitHubRepository.IssueDataCached {

    int number;
    String title;
    String body;
    UserData user;

    @Expose(deserialize = false) State state;
    @Expose(deserialize = false) StateReason state_reason;
    @Expose(deserialize = false) TypeData type;
    OffsetDateTime created_at;
    @Nullable OffsetDateTime closed_at;

    boolean isPullRequest = false;

    @SerializedName(value = "url", alternate = {"html_url"}) String url;

    private List<ReferencedLink<String>> commentsList;
    private List<EventData> eventsList;
    private List<ReviewData> reviewsList;
    private List<ReferencedLink<GitHubCommit>> relatedCommits;
    List<ReferencedLink<Integer>> relatedIssues;

    transient GitHubRepository repo;
    private transient boolean frozen;

    /**
     * Creates a new IssueData object.
     */
    IssueData() { }

    /**
     * Sets a list of Comments to this Issue.
     *
     * @param comments
     *         the Comment list
     */
    void setComments(List<ReferencedLink<String>> comments) {
        commentsList = comments;
    }

    /**
     * Sets a list of Events to this Issue.
     *
     * @param events
     *         the Event list.
     */
    void setEvents(List<EventData> events) {
        eventsList = events;
    }

    /**
     * Sets a list of Reviews to this Issue.
     *
     * @param reviews
     *         the Review list
     */
    void setReviews(List<ReviewData> reviews) {
        reviewsList = reviews;
    }

    /**
     * Sets a list of related Commits to this Issue.
     *
     * @param commits
     *         the Commit list
     */
    void setRelatedCommits(List<ReferencedLink<GitHubCommit>> commits) {
        relatedCommits = commits;
    }

    /**
     * Sets a list of related Issues (rather their numbers) to this Issue
     * from links containing just issues numbers.
     *
     * @param issues
     *         the issue list (composed of Integers)
     * @see IssueDataProcessor#parseIssues(IssueData, Gson)
     */
    void setRelatedIssues(List<ReferencedLink<Integer>> issues) {
        relatedIssues = issues.stream().map(issue ->
                new ReferencedLink<>(issue.target, issue.user, issue.referenced_at)
        ).collect(Collectors.toList());
    }

    /**
     * Sets a list of related Issues (rather their numbers) to this Issue
     * from links containing IssueData objects.
     *
     * @param issues
     *         the issue list (composed of IssueData objects)
     */
    void setRelatedIssuesFromIssueData(List<ReferencedLink<IssueData>> issues) {
        relatedIssues = issues.stream().map(issue ->
                new ReferencedLink<>(((IssueData) issue.target).number, issue.user, issue.referenced_at)
        ).collect(Collectors.toList());
    }

    /**
     * Before accessing data for the first time, init, sort and lock all data once.
     */
    void freeze() {
        if (frozen) return;

        Comparator<ReferencedLink> compare = Comparator.comparing(ReferencedLink::getLinkTime);

        if (reviewsList == null) {
            reviewsList = new ArrayList<>();
        }

        eventsList = Collections.unmodifiableList(eventsList.stream()
                .filter(Objects::nonNull).sorted(Comparator.comparing(link -> link.created_at)).collect(Collectors.toList()));
        commentsList = Collections.unmodifiableList(commentsList.stream()
                .filter(Objects::nonNull).sorted(compare).collect(Collectors.toList()));
        reviewsList = Collections.unmodifiableList(reviewsList.stream()
                .filter(Objects::nonNull).sorted(Comparator.comparing(link -> link.submitted_at)).collect(Collectors.toList()));
        relatedIssues = Collections.unmodifiableList(relatedIssues.stream()
                .filter(Objects::nonNull).distinct().sorted(compare).collect(Collectors.toList()));
        relatedCommits = Collections.unmodifiableList(relatedCommits.stream()
                // Remove invalid commits before they cause problems
                .filter(c -> c != null && c.getTarget() != null && c.getTarget().getAuthorTime() != null)
                .distinct().sorted(compare).collect(Collectors.toList()));

        frozen = true;
    }

    /**
     * Gets the number of the issue (referenced with {@code #nr}).
     *
     * @return the number
     */
    public int getNumber() {
        return number;
    }

    /**
     * Gets data about the User that created the issue.
     *
     * @return the user
     */
    public UserData getUser() {
        return user;
    }

    /**
     * Gets information about the state of the issue.
     *
     * @return the state
     */
    public State getState() {
        return state;
    }

    /**
     * Gets the reason for the current state of the issue.
     *
     * @return the state reason
     */
    public StateReason getStateReason() {
        return state_reason;
    }

    /**
     * Gets the date and time the issue was created.
     *
     * @return date and time the issue was created
     */
    public OffsetDateTime getCreateDate() {
        return created_at;
    }

    /**
     * Gets he date and time the issue was closed.
     *
     * @return date and time the issue was closed, or {@code null} if it is still open.
     */
    @Nullable
    public OffsetDateTime getCloseDate() {
        return closed_at;
    }

    /**
     * Checks if this issue is a pull request.
     *
     * @return {@code true} if it is a PullRequest.
     * @see PullRequestData
     * @see PullRequest
     */
    public boolean isPullRequest() {
        return isPullRequest;
    }

    /**
     * Gets the title of the issue.
     *
     * @return the text title.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gets teh text body.
     *
     * @return the text body.
     */
    public String getBody() {
        return body;
    }

    /**
     * Gets the HTML URL to this issue.
     *
     * @return the URL
     */
    public String getUrl() {
        return url;
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
     * Gets a List all Reviews.
     *
     * @return a List of ReviewData
     */
    public List<ReviewData> getReviewsList() {
        return reviewsList;
    }

    /**
     * Gets a List of all Commits referenced in the Issue, its Comments and Events.
     *
     * @return a List of Commits in form of ReferencedLink<GitHubCommit>
     */
    public List<ReferencedLink<GitHubCommit>> getRelatedCommits() {
        return relatedCommits;
    }

    /**
     * Gets a List of all Issues referenced in the Issue and its Comments.
     *
     * @return a List of Issues in form of ReferencedLink<IssueData>
     */
    public List<ReferencedLink<IssueData>> getRelatedIssues() {
        List<ReferencedLink<IssueData>> issues = new ArrayList<>();

        for(ReferencedLink<Integer> relatedIssue : relatedIssues) {
            IssueData cachedIssue = repo.getIssueFromCache((Integer) relatedIssue.target);
            ReferencedLink<IssueData> issue = new ReferencedLink<>(cachedIssue, relatedIssue.user, relatedIssue.referenced_at);
            issues.add(issue);
        }
        return issues;
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
