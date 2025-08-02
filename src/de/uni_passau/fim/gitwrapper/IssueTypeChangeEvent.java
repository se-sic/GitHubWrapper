/**
 * Copyright (C) 2016-2018 Florian Heck
 * Copyright (C) 2019-2020 Thomas Bock
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
 * Represents the type of issue type change event.
 */
public enum IssueTypeChangeEvent {
    /**
     * An issue type was added to the issue.
     */
    ADDED,

    /**
     * An issue type was changed for the issue.
     */
    CHANGED,

    /**
     * An issue type was removed from the issue.
     */
    REMOVED,

    /**
     * Default value for unknown or unspecified issue type change events.
     */
    ANY;

    /**
     * Gets the IssueTypeChangeEvent from a string.
     *
     * @param string the string to convert
     * @return the corresponding IssueTypeChangeEvent, or null if no match is found
     */
    public static IssueTypeChangeEvent getFromString(String string) {
        if (string == null) {
            return ANY;
        }

        switch (string.toLowerCase()) {
            case "issue_type_added":
                return ADDED;
            case "issue_type_changed":
                return CHANGED;
            case "issue_type_removed":
                return REMOVED;
            default:
                return null;
        }
    }
}