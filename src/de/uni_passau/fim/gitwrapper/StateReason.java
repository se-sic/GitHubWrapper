/**
 * Copyright (C) 2025 Shiraz Jafri
 * Copyright (C) 2025-2026 Leo Sendelbach
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
 * Enumeration of reasons for the state of an Issue or PullRequest.
 */
public enum StateReason {
    /**
     * Denotes that the Issue or PullRequest was completed.
     */
    COMPLETED,

    /**
     * Denotes that the Issue or PullRequest was reopened.
     */
    REOPENED,

    /**
     * Denotes that the Issue or PullRequest was not planned.
     */
    NOT_PLANNED,

    /**
     * Denotes that the Issue or PullRequest was a duplicate.
     */
    DUPLICATE;

    /**
     * Gets the StateReason from a string.
     *
     * @param string the string to convert
     * @return the corresponding StateReason, the default case being 'COMPLETED'
     */
    public static StateReason getFromString(String string) {
        if (string == null) {
            return COMPLETED;
        }

        switch (string.toLowerCase()) {
            case "completed":
                return COMPLETED;
            case "reopened":
                return REOPENED;
            case "not_planned":
                return NOT_PLANNED;
            case "duplicate":
                return DUPLICATE;
            default:
                throw new IllegalArgumentException("Found state reason (" + string + ") that was neither 'completed', " +
                                                    "'reopened', 'not_planned', nor 'duplicate'!");
        }
    }
}
