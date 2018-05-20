package de.uni_passau.fim.gitwrapper;

import java.util.Date;

/**
 * A skeleton object to deserialize a {@link PullRequest}.
 */
public class PullRequestData extends IssueData {

    RefData head;
    RefData base;
    Date merged_at;

    /**
     * For use by the deserializer.
     */
    public PullRequestData() {
        this.isPullRequest = true;
    }

    /**
     * Info about the head/tip of the PullRequest.
     */
    public RefData getHead() {
        return head;
    }

    /**
     * Info about the base/target of the PullRequest.
     */
    public RefData getBase() {
        return base;
    }

    /**
     * The date and time the PullRequest was merged, or null if it was declined or is still open.
     */
    public Date getMergedDate() {
        return merged_at;
    }
}
