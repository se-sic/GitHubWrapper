package de.uni_passau.fim.heck.githubinterface;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import de.uni_passau.fim.seibt.gitwrapper.repo.Branch;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;
import de.uni_passau.fim.seibt.gitwrapper.repo.Reference;

public class PullRequest extends Branch {

    public final String state;
    public final Reference targetBranch;

    /**
     * Adds a PullRequest to the given repo {@code repo}.
     *
     * @param repo
     *         the {@link Repository} this {@link Reference} belongs to
     * @param id
     *         the ID of the {@link Reference}
     * @param forkURL
     * @param state
     * @param targetBranch
     */
    protected PullRequest(Repository repo, String id, String remoteName, String forkURL, String state, Reference targetBranch) {
        super(repo, id);
        this.state = state;
        this.targetBranch = targetBranch;
        repo.addRemote(remoteName, forkURL);
    }

    public Optional<Reference> getMergeTarget() {
        Optional<Date> date = getTip().map(c -> Date.from(c.getAuthorTime().toInstant()));
        List<Commit> history = date.flatMap(repo::getCommitsBeforeDate).orElse(Collections.emptyList());

        return getMergeBase(targetBranch).map(c -> {
            // return first commit before the last commit in the pull request, which is has the merge base as an ancestor
            for (Commit commit : history) if (commit.checkAncestry(c).orElse(false)) return commit;
            return null;
        });
    }

    public Optional<Commit> getMergeBase() {
        return super.getMergeBase(targetBranch);
    }
}
