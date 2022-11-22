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
 * Skeleton for information about git references.
 */
public class RefData {

    String ref;
    String sha;
    RepoData repo;

    /**
     * The symbolic reference name.
     */
    public String getRef() {
        return ref;
    }

    /**
     * The hash of the tip.
     */
    public String getSha() {
        return sha;
    }

    /**
     * Information about the repository
     */
    public RepoData getRepo() {
        return repo;
    }
}
