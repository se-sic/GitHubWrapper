/**
 * Copyright (C) 2016-2018 Florian Heck
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

import java.util.*;
import java.util.logging.Logger;

/**
 * A git reference (branch pointer) providing access to the GitHub data about the pull requests represented by this
 * branch.
 */
public class PullRequest extends Reference {

    private static final Logger LOG = Logger.getLogger(PullRequest.class.getCanonicalName());

    private final State state;
    private final Reference targetBranch;
    private List<Commit> commits;

    private final GitHubRepository repo;
    private final PullRequestData issue;

    /**
     * Adds a PullRequest to the given repo {@code repo}.
     *
     * @param repo
     *         the local Repository representation of the GitHub repository
     * @param state
     *         the sate of the pull request
     * @param targetBranch
     *         the target branch
     * @param commits
     *         A list of Commits included in this PullRequest
     * @param issue
     *         the corresponding pull request in GitHub
     */
    PullRequest(GitHubRepository repo, State state, Reference targetBranch, List<Commit> commits, PullRequestData issue) {
        super(repo, issue.branch);
        this.state = state;
        this.targetBranch = targetBranch;
        this.commits = commits;
        this.repo = repo;
        this.issue = issue;
    }

    /**
     * Determines the commit which will be the second parent in a hypothetical merge, or return the actual merge partner
     * if the merge was already carried out.
     *
     * @return optionally the other reference for the merge, or an empty Optional, if the operations failed
     */
    public Optional<Reference> getMergeTarget() {
        // All merged pull requests are a problem since both sides are in the history of the target branch, so we need
        // to handle all merged pull requests differently by looking at the parents of the actual merge and using the
        // parent that is not in the tip of the pull request, since that is the commit that was merged into
        if (isMerged()) {
            return getMerge().map(m -> m.commit).orElseGet(() -> { LOG.warning("Could not find merge for PR " + id); return repo.getCommitUnchecked("impossible commit"); })
                    .getParents().orElseGet(() -> { LOG.warning("Could not find parents of the merge of PR " + id); return new ArrayList<>();}).stream()
                    .filter(p -> !p.equals(getTip().orElseGet(() -> { LOG.warning("Could not find tip of PR " + id); return null; })))
                    .findFirst().map(x -> x);
        }

        // If the branch that was merged *into* was deleted, we don't have a valid target branch.
        if (targetBranch == null) {
            LOG.fine("Target branch was probably deleted.");
            return Optional.empty();
        }

        // If it is open, we just take the current tip of the target branch
        if (state == State.OPEN) {
            return Optional.of(targetBranch);
        }

        Optional<Date> date = getTip().map(c -> Date.from(c.getAuthorTime().toInstant()));
        List<Commit> history = date.flatMap(d -> repo.getCommitsBeforeDate(d, targetBranch.getId())).orElse(Collections.emptyList());

        // Otherwise we return the first commit in the target branch before the last commit in the pull request
        // (which has the merge base as an ancestor)
        return getMergeBase(targetBranch).map(c -> {
            for (Commit commit : history)
                if (c.checkAncestry(commit).orElse(false))
                    return commit;
            return null;
        });
    }

    /**
     * Gets the Commit at the tip of this PullRequest.
     *
     * @return optionally the Commit at the tip, or an empty Optional, if the operations failed
     */
    public Optional<Commit> getTip() {
        return repo.getCommit(issue.head.sha);
    }

    /**
     * Determines the merge base between the merge target ({@link #getMergeTarget}) and the tip ({@link #getTip}).
     *
     * @return optionally the Commit that constitutes the merge base, or an empty Optional, if the operations failed
     */
    public Optional<Commit> getMergeBase() {
        return getMergeTarget().flatMap(this::getMergeBase);
    }

    /**
     * Gets the state of this pull request, either {@code closed} or {@code open}.
     *
     * @return the state as the String used by GitHub
     */
    public State getState() {
        return state;
    }

    /**
     * Returns {@code true} if there was an event which closed the PullRequest and there is a merge date in the
     * {@link PullRequestData}.
     *
     * @return {@code true}, if this PullRequest is merged
     */
    private boolean isMerged() {
        return state == State.MERGED;
    }

    /**
     * Gets the EventData corresponding to the merge.
     *
     * @return optionally the ReferencedEventData for the merge, or an empty Optional if there is none
     */
    private Optional<EventData.ReferencedEventData> getMerge() {
        // find a merge event
        return issue.getEventsList().stream().filter(e -> e.event.equals("merged")).findFirst().map(e -> ((EventData.ReferencedEventData) e));
    }

    /**
     * Gets the corresponding GitHub issue to this pull request.
     *
     * @return the IssueData
     */
    public IssueData getIssue() {
        return issue;
    }

    /**
     * Returns a List of all Commits that are included in this PullRequest but not in the history of the
     * {@link #getMergeTarget() target}.
     *
     * @return optionally a List of the Commits, or an empty Optional, if the operation failed
     * @see #getMergeBase(), {@link #getCommits()}
     */
    @Deprecated
    public Optional<List<Commit>> getCommitsLocal() {
        return getMergeBase().flatMap(base -> getTip().map(tip -> {
            Queue<Commit> next = new ArrayDeque<>();
            List<Commit> commits = new ArrayList<>();

            Commit c = tip;
            do {
                commits.add(c);
                c.getParents().ifPresent(next::addAll);
                do {
                    c = next.poll();
                } while (c != null && c.equals(base));
            } while (c != null);
            return commits;
        }));
    }

    /**
     * Returns a List of all Commits that are included in this PullRequest
     *
     * @return a List of the Commits
     * @see #getMergeBase()
     */
    public List<Commit> getCommits() {
        return commits;
    }
}
