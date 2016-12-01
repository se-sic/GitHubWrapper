package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IssueData {
    public int number;
    public UserData user;
    public String comments_url;

    private List<CommentData> comments = new ArrayList<>();

    public void addComment(CommentData comment) {
        comments.add(comment);
    }

    public List<CommentData> getComments() {
        return Collections.unmodifiableList(comments);
    }
}
