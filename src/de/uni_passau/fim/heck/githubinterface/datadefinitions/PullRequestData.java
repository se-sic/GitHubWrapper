package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PullRequestData extends IssueData {

    public Ref head;
    public Ref base;

    public PullRequestData() {
        this.isPullRequest = true;
    }

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
