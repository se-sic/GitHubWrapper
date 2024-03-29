/**
 * Copyright (C) 2017-2018 Florian Heck
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
     * @see #MERGED
     * @see #DECLINED
     */
    CLOSED,

    /**
     * Denotes merged/accepted PullRequests.
     */
    MERGED,

    /**
     * Denotes open and declined PullRequests.
     * @see #OPEN
     * @see #DECLINED
     */
    UNMERGED,

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
     * Helper method to for filtering, {@link #ANY} includes all, {@link #CLOSED} includes {@link #MERGED} and
     * {@link #DECLINED}, and {@link #UNMERGED} includes {@link #OPEN} and {@link #DECLINED}.
     *
     * @param state
     *         the state to check
     * @param filter
     *         the filter State
     * @return {@code true}, if {@code state} is included in {@code filter}
     */
    public static boolean includes(State state, State filter) {
        return filter == state ||
               filter == ANY ||
              (filter == UNMERGED &&
                       (state == OPEN ||
                        state == DECLINED)) ||
              (filter == CLOSED &&
                        (state == MERGED ||
                         state == DECLINED));
    }
}
