package de.uni_passau.fim.gitwrapper;

import java.time.OffsetDateTime;

/**
 * A skeleton object for Comments.
 */
public class CommentData {

    UserData user;
    OffsetDateTime created_at;
    String body;

    /**
     * Information about the User that crated the Comment.
     */
    public UserData getUser() {
        return user;
    }

    /**
     * The date an time the Comment was created.
     */
    public OffsetDateTime getCreated_at() {
        return created_at;
    }

    /**
     * Get the text of the comment.
     */
    public String getBody() {
        return body;
    }
}
