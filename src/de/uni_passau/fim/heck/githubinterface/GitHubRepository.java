package de.uni_passau.fim.heck.githubinterface;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.CommentData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.IssueData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.PullRequestData;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;

public class GitHubRepository {

    private static final Logger LOG = Logger.getLogger(GitHubRepository.class.getCanonicalName());
    private final Repository repo;
    private String apiBaseURL;

    public GitHubRepository(Repository repo) {
        this.repo = repo;
        String repoUrl = repo.getUrl();
        if (repoUrl.contains("git@")) {
            repoUrl = repoUrl.replace(":", "/").replace("git@", "https://");
        }
        apiBaseURL = repoUrl.replace(".git", "").replace("//github.com/", "//api.github.com/repos/");
    }

    public Optional<List<PullRequest>> getPullRequests() {
        URL url;
        InputStreamReader reader;
        try {
            url = new URL(apiBaseURL + "/pulls?per_page=100&state=all");
            reader = new InputStreamReader(url.openStream());
        } catch (IOException e) {
            LOG.warning("Could not get list of pull requests from Github.");
            return Optional.empty();
        }

        ArrayList<PullRequestData> data = new Gson().fromJson(reader, new TypeToken<ArrayList<PullRequestData>>(){}.getType());
        return Optional.of(data.stream().map(pr ->
                new PullRequest(repo, pr.head.ref, pr.head.repo.full_name + "/" + pr.number,
                        pr.head.repo.html_url, pr.state, repo.getBranch(pr.base.ref).get())
        ).collect(Collectors.toList()));
    }

    public Optional<List<IssueData>> getIssues() {
        URL url;
        InputStreamReader reader;
        try {
            url = new URL(apiBaseURL + "/issues?per_page=100&state=all");
            reader = new InputStreamReader(url.openStream());
        } catch (IOException e) {
            LOG.warning("Could not get list of pull requests from Github.");
            return Optional.empty();
        }
        ArrayList<IssueData> data = new Gson().fromJson(reader, new TypeToken<ArrayList<IssueData>>(){}.getType());
        data.forEach(issue -> {
            Optional<List<CommentData>> comments = getComments(issue);
            comments.ifPresent(list -> list.forEach(issue::addComment));
        });
        return Optional.of(data);
    }

    public Optional<List<CommentData>> getComments(IssueData issue) {
        URL url;
        InputStreamReader reader;
        try {
            url = new URL(apiBaseURL + "/issues/" + issue.number + "/comments?per_page=100&state=all");
            reader = new InputStreamReader(url.openStream());
        } catch (IOException e) {
            LOG.warning("Could not get list of pull requests from Github.");
            return Optional.empty();
        }
        return Optional.of(new Gson().fromJson(reader, new TypeToken<ArrayList<CommentData>>() {}.getType()));
    }

    public Repository getRepo() {
        return repo;
    }
}
