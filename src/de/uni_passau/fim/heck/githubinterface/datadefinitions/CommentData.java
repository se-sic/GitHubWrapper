package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.Date;

/**
 * A skeleton object for Comments.
 */
public class CommentData {

    /**
     * Information about the User that crated the Comment.
     */
    public UserData user;

    /**
     * The date an time the Comment was created.
     */
    public Date created_at;

    /**
     * The text of the comment.
     */
    public String body;
}
