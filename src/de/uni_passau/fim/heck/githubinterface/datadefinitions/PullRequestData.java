package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import de.uni_passau.fim.heck.githubinterface.PullRequest;

import java.util.Date;

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

    /**
     * The date and time the PullRequest was merged, or null if it was declined or is still open.
     */
    public Date merged_at;

    /**
     * For use by the deserializer.
     */
    public PullRequestData() {
        this.isPullRequest = true;
    }
}
