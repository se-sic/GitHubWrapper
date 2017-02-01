package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import de.uni_passau.fim.heck.githubinterface.PullRequest;

/**
 * A skeleton object to deserialize a {@link PullRequest}.
 */
public class PullRequestData extends IssueData {

    /**
     * Info about the head/tip of the PullRequest.
     */
    public RefData head;

    /**
     * Info about the base/target of the PullRequest.
     */
    public RefData base;

    public PullRequestData() {
        this.isPullRequest = true;
    }
}
