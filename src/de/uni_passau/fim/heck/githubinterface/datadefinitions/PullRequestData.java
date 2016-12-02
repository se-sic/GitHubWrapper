package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PullRequestData {

    public int number;
    public String state;
    public Date created_at;
    public Ref head;
    public Ref base;
    public UserData user;

    private List<CommentData> commentList = new ArrayList<>();

    public class Ref {
        public String ref;
        public RepoData repo;
    }

    public void addComment(CommentData comment) {
        commentList.add(comment);
    }

    public List<CommentData> getCommentList() {
        return Collections.unmodifiableList(commentList);
    }
}
