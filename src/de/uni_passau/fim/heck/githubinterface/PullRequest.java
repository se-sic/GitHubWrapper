package de.uni_passau.fim.heck.githubinterface;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.IssueData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.PullRequestData;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;
import de.uni_passau.fim.seibt.gitwrapper.repo.Reference;

public class PullRequest extends Reference {

    private final String state;
    private final Reference targetBranch;

    private final GitHubRepository repo;
    private final PullRequestData issue;

    /**
     * Adds a PullRequest to the given repo {@code repo}.
     *
     * @param repo
     *         the local repository representation of the github repository
     * @param id
     *         the branch name
     * @param remoteName
     *         the unique identifier (user/branch/pr-number)
     * @param forkURL
     *         the url of the forked repo
     * @param state
     *         the sate of the pull request
     * @param targetBranch
     *         the target branch
     * @param issue
     *         the corresponding pull request in GitHub
     */
    PullRequest(GitHubRepository repo, String id, String remoteName, String forkURL, String state, Reference targetBranch, PullRequestData issue) {
        super(repo, remoteName + "/" + id);
        this.state = state;
        this.targetBranch = targetBranch;
        repo.addRemote(remoteName, forkURL);
        this.repo = repo;
        this.issue = issue;
    }

    public Optional<Reference> getMergeTarget() {
        Optional<Date> date = getTip().map(c -> Date.from(c.getAuthorTime().toInstant()));
        List<Commit> history = date.flatMap(repo::getCommitsBeforeDate).orElse(Collections.emptyList());

        return getMergeBase(targetBranch).map(c -> {
            // return first commit before the last commit in the pull request, which has the merge base as an ancestor
            for (Commit commit : history) if (commit.checkAncestry(c).orElse(false)) return commit;
            return null;
        });
    }

    public Optional<Commit> getTip() {
        return repo.getCommit(issue.head.sha);
    }

    public Optional<Commit> getMergeBase() {
        return super.getMergeBase(targetBranch);
    }

    @Override
    public String getId() {
        return getTip().get().getId();
    }

    public String getState() {
        return state;
    }

    private boolean isMerged() {
        return state.equals("closed") && getMerge().isPresent();
    }

    private Optional<EventData.ReferencedEventData> getMerge() {
        // find a merge event
        return issue.getEventsList().stream().filter(e -> e.event.equals("merged")).findFirst().map(e -> ((EventData.ReferencedEventData) e));
    }

    public IssueData getIssue() {
        return issue;
    }
}
