package de.uni_passau.fim.heck.githubinterface.datadefinitions;

/**
 * Enumeration of states an Issue or PullRequest can be in.
 */
public enum State {

    /**
     * Denotes open Issues ans PullRequests.
     */
    OPEN,

    /**
     * Denote closed Issued and PullRequests.
     */
    CLOSED,

    /**
     * Denotes merged/accepted PullRequests.
     */
    MERGED,

    /**
     * Denotes declined/rejected PullRequests.
     */
    DECLINED,

    /**
     * Any state is included (can be used for unknown states).
     */
    ANY;

    /**
     * Gets the correct State from the provided String.
     *
     * @param string
     *         the string representation of the State
     * @return the State
     */
    public static State getFromString(String string) {
        switch (string.toLowerCase()) {
            case "open":
                return OPEN;
            case "closed":
                return CLOSED;
            case "declined":
                return DECLINED;
            case "merged":
                return MERGED;
            default:
                return ANY;
        }
    }

    /**
     * Gets the correct State representation when used in regard to PullRequests (differentiated between merged and
     * declined for closed)
     *
     * @param state
     *         the Issue State
     * @param merged
     *         if it was merged
     * @return the State
     */
    public static State getPRState(State state, boolean merged) {
        if (state == CLOSED) {
            return merged ? MERGED : DECLINED;
        }
        return state;
    }

    /**
     * Helper method to for filtering, {@link #ANY} includes all, {@link #CLOSED} includes {@link #MERGED} and  {@link #DECLINED}
     *
     * @param state
     *         the state to check
     * @param filter
     *         the filter State
     * @return {@code true}, if {@code state} is included in {@code filter}
     */
    public static boolean includes(State state, State filter) {
        return filter == ANY ||
              (filter == State.CLOSED &&
                      (state == State.MERGED ||
                       state == State.DECLINED)) ||
               filter == state;
    }
}
