package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IssueData {
    public int number;
    public UserData user;

    private List<CommentData> commentsList = new ArrayList<>();

    public void addComment(CommentData comment) {
        commentsList.add(comment);
    }

    public List<CommentData> getCommentsList() {
        return Collections.unmodifiableList(commentsList);
    }
}
