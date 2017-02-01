package de.uni_passau.fim.heck.githubinterface;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.logging.Logger;

import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.IssueData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.PullRequestData;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;
import de.uni_passau.fim.seibt.gitwrapper.repo.Reference;

public class PullRequest extends Reference {

    private static final Logger LOG = Logger.getLogger(PullRequest.class.getCanonicalName());

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
        // All merged pull requests are a problem since both sides are in the history of the target branch, so we need
        // to handle all merged pull requests differently by looking at the parents of the actual merge and using the
        // parent that is not in the tip of the pull request, since that is the commit that it was merged into
        if (isMerged()) {
            return Optional.of(repo.getCommitUnchecked(getMerge().get().commit_id)
                    .getParents().orElseGet(ArrayList::new).stream().filter(p -> !p.equals(getTip().orElse(null)))
                    .findFirst().orElse(null));
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

    public Optional<Commit> getTip() {
        return repo.getCommit(issue.head.sha);
    }

    public Optional<Commit> getMergeBase() {
        Optional<Commit> gitBase = getMergeTarget().flatMap(this::getMergeBase);
        Commit githubBase = repo.getCommitUnchecked(issue.base.sha);

        if (!gitBase.map(a -> a.equals(githubBase)).orElse(false)) {
            LOG.warning("GitHub does not match local findings about mergebase!");
            return Optional.empty();
        }
        return gitBase;
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

    public Optional<List<Commit>> getCommits() {
        return getMergeBase().flatMap(base -> getTip().map(tip -> {
            Queue<Commit> next = new ArrayDeque<>();
            List<Commit> commits = new ArrayList<>();

            Commit c = tip;
            do {
                commits.add(c);
                c.getParents().ifPresent(next::addAll);
                c = next.poll();
            } while(c != null && !c.equals(base));
            return commits;
        }));
    }
}
