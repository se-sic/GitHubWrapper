package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.Objects;

/**
 * Data representation of a GitHub user.
 */
public class UserData {

    /**
     * The username
     */
    public String username;

    /**
     * The email address
     */
    public String email;

    /**
     * The name
     */
    public String name;

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
}
