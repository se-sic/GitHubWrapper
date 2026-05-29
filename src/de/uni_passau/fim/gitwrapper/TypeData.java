/**
 * Copyright (C) 2025 Shiraz Jafri
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
 * Skeleton object for data about issue types from GitHub's API.
 */
public class TypeData {
    String name;
    String description;

    /**
     * The name of the type.
     */
    public String getName() {
        return name;
    }

    /**
     * The description of the type.
     */
    public String getDescription() {
        return description;
    }
}
