/**
 * Copyright (C) 2016-2018 Florian Heck
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

import java.util.Date;

/**
 * A skeleton object to deserialize a {@link PullRequest}.
 */
public class PullRequestData extends IssueData {

    RefData head;
    RefData base;
    Date merged_at;
    String branch;

    /**
     * For use by the deserializer.
     */
    PullRequestData() {
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

    /**
     * The name of the branch/reference.
     */
    public String getBranch() {
        return branch;
    }
}
