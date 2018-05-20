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

    static class CommitUserData {
        String name;
        String email;
        OffsetDateTime date;

        CommitUserData(String name, String email, OffsetDateTime date) {
            this.name = name;
            this.email = email;
            this.date = date;
        }
    }
}
