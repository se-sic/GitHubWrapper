/**
 * Copyright (C) 2018 Florian Heck
 * Copyright (C) 2019 Thomas Bock
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

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Data representation of a GitHub user.
 */
public class UserData {

    String username;
    String email;
    String name;

    /**
     * The username
     */
    public String getUsername() {
        return username;
    }

    /**
     * The email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * The name
     */
    public String getName() {
        return name;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserData)) return false;
        UserData userData = (UserData) o;
        return Objects.equals(username, userData.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    /**
     * Data class for information about the users in commits.
     */
    static class CommitUserData {
        String name;
        String email;
        String githubUsername;
        OffsetDateTime date;

        /**
         * Creates a new CommitUserData object.
         *
         * @param name
         *         the users name as known to git
         * @param email
         *         the users email
         * @param date
         *         the date ot the commit
         */
        CommitUserData(String name, String email, OffsetDateTime date) {
            this.name = name;
            this.email = email;
            this.date = date;
        }

        CommitUserData(String name, String email, String username, OffsetDateTime date) {
            this.name = name;
            this.email = email;
            this.date = date;
            this.githubUsername = username;
        }
    }
}
