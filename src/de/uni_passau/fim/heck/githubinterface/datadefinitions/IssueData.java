package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;

public class IssueData {

    public int number;
    public UserData user;
    public String state;
    public Date created_at;
    public Date closed_at;
    public boolean isPullRequest;
    public String body;

    @SerializedName(value = "url", alternate = {"html_url"})
    public String url;

    private List<CommentData> commentsList = new ArrayList<>();
    private List<EventData> eventsList = new ArrayList<>();
    private List<Commit> relatedCommits = new ArrayList<>();

    public void addComment(CommentData comment) {
        commentsList.add(comment);
    }

    public void addEvent(EventData event) {
        eventsList.add(event);
    }

    public void addRelatedCommit(Commit commit) {
        relatedCommits.add(commit);
    }

    public List<CommentData> getCommentsList() {
        return Collections.unmodifiableList(commentsList);
    }

    public List<EventData> getEventsList() {
        return Collections.unmodifiableList(eventsList);
    }

    public List<Commit> getRelatedCommits() {
        return Collections.unmodifiableList(relatedCommits);
    }
}
