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
 * Skeleton object for data about Repositories.
 */
public class RepoData {

    String full_name;
    String html_url;
    String clone_url;

    /**
     * The full name of the Repository.
     */
    public String getFullName() {
        return full_name;
    }

    /**
     * The HTML URL to the web page on GitHub.
     */
    public String getHtmlUrl() {
        return html_url;
    }

    /**
     * The url to clone the repo.
     */
    public String getCloneUrl() {
        return clone_url;
    }
}
