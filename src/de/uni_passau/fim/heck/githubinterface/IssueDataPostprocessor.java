package de.uni_passau.fim.heck.githubinterface;

import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.CommentData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.IssueData;
import io.gsonfire.PostProcessor;

public class IssueDataPostprocessor implements PostProcessor<IssueData> {

    private final GitHubRepository repo;

    public IssueDataPostprocessor(GitHubRepository repo) {
        this.repo = repo;
    }

    @Override
    public void postDeserialize(IssueData result, JsonElement src, Gson gson) {
        result.isPullRequest = src.getAsJsonObject().get("pull_request") != null;
        Optional<List<CommentData>> comments = repo.getComments(result);
        Optional<List<EventData>> events = repo.getEvents(result);

        comments.ifPresent(list -> list.forEach(result::addComment));
        events.ifPresent(list -> list.forEach(result::addEvent));
    }

    @Override
    public void postSerialize(JsonElement result, IssueData src, Gson gson) {

    }
}