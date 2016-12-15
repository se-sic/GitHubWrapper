package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class IssueData {

    public int number;
    public UserData user;
    public String state;
    public Date created_at;
    public Date closed_at;
    public boolean isPullRequest;

    private List<CommentData> commentsList = new ArrayList<>();
    private List<EventData> eventsList = new ArrayList<>();

    public void addComment(CommentData comment) {
        commentsList.add(comment);
    }

    public void addEvent(EventData event) {
        eventsList.add(event);
    }

    public List<CommentData> getCommentsList() {
        return Collections.unmodifiableList(commentsList);
    }

    public List<EventData> getEventsList() {
        return Collections.unmodifiableList(eventsList);
    }
}
