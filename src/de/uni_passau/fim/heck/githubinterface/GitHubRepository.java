package de.uni_passau.fim.heck.githubinterface;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.CommentData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventDataDeserializer;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.IssueData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.PullRequestData;
import de.uni_passau.fim.seibt.gitwrapper.process.ProcessExecutor;
import de.uni_passau.fim.seibt.gitwrapper.repo.BlameLine;
import de.uni_passau.fim.seibt.gitwrapper.repo.Branch;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;
import de.uni_passau.fim.seibt.gitwrapper.repo.GitWrapper;
import de.uni_passau.fim.seibt.gitwrapper.repo.MergeConflict;
import de.uni_passau.fim.seibt.gitwrapper.repo.Reference;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;
import de.uni_passau.fim.seibt.gitwrapper.repo.Status;

public class GitHubRepository extends Repository {

    private static final Logger LOG = Logger.getLogger(GitHubRepository.class.getCanonicalName());
    private final Repository repo;

    private final String apiBaseURL;
    private final String oauthToken;
    private final GitWrapper git;
    private final File dir;

    private Gson gson;

    /**
     * Create a wrapper around a (local) repository with additional information about Github hosted repositories.
     *
     * @param repo
     *         the local repository
     * @param git
     *         the GitWrapper instance to use
     */
    public GitHubRepository(Repository repo, GitWrapper git, String oauthToken) {
        this.repo = repo;
        String repoUrl = repo.getUrl();
        if (repoUrl.contains("git@")) {
            repoUrl = repoUrl.replace(":", "/").replace("git@", "https://");
        }
        apiBaseURL = repoUrl.replace(".git", "").replace("//github.com/", "//api.github.com/repos/");
        this.git = git;
        dir = repo.getDir();
        this.oauthToken = oauthToken;

        GsonBuilder gb = new GsonBuilder();
        gb.registerTypeAdapter(EventData.class, new EventDataDeserializer());
        gson = gb.create();
    }

    /**
     * Gets a list of all PullRequests.
     *
     * @return optionally a list of PullRequests or an empty Optional, if an error occured
     */
    public Optional<List<PullRequest>> getPullRequests() {
        return getJSONReaderFromURL("/pulls?per_page=100&state=all").map(reader -> {
            ArrayList<PullRequestData> data = gson.fromJson(reader, new TypeToken<ArrayList<PullRequestData>>() {}.getType());
            return data.stream().filter(pr -> !pr.state.equals("closed")).map(pr ->
                    new PullRequest(this, pr.head.ref, pr.head.repo.full_name + "/" + pr.number,
                            pr.head.repo.html_url, pr.state, repo.getBranch(pr.base.ref).get())
            ).collect(Collectors.toList());
        });
    }

    /**
     * Gets a list of Issues.
     *
     * @return optionally a list of IssueData or an empty Optional if an error occurred
     */
    public Optional<List<IssueData>> getIssues() {
        return getJSONReaderFromURL("/issues?per_page=100&state=all").map(reader -> {
            ArrayList<IssueData> data = gson.fromJson(reader, new TypeToken<ArrayList<IssueData>>() {}.getType());
            data.forEach(issue -> {
                Optional<List<CommentData>> comments = getComments(issue);
                Optional<List<EventData>> events = getEvents(issue);

                comments.ifPresent(list -> list.forEach(issue::addComment));
                events.ifPresent(list -> list.forEach(issue::addEvent));
            });
            return data;
        });
    }

    /**
     * Returns a list of events for an Issue
     *
     * @param issue
     *         the parent IssueData
     * @return optionally a list of EventData or an empty Optional if an error occurred
     */
    Optional<List<EventData>> getEvents(IssueData issue) {
        return getJSONReaderFromURL("/issues/" + issue.number + "/events?per_page=100").map(reader->
                gson.fromJson(reader, new TypeToken<ArrayList<EventData>>() {}.getType())
        );
    }

    /**
     * Returns a list of comments for an Issue
     *
     * @param issue
     *         the parent IssueData
     * @return optionally a list of CommentData or an empty Optional if an error occurred
     */
    Optional<List<CommentData>> getComments(IssueData issue) {
        return getJSONReaderFromURL("/issues/" + issue.number + "/comments?per_page=100&state=all").map(reader ->
                gson.fromJson(reader, new TypeToken<ArrayList<CommentData>>() {}.getType())
        );
    }

    /**
     * Gets a list of all commits before a given date.
     *
     * @param date
     *         the date until commits are included
     * @return optionally a list of {@link Commit commits} or an empty optional if the operation failed
     */
    Optional<List<Commit>> getCommitsBeforeDate(Date date) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        Optional<ProcessExecutor.ExecRes> commitList = git.exec(dir, "log", "--format=tformat:%H", "--branches=*", "--until=" + df.format(date));
        Function<ProcessExecutor.ExecRes, List<Commit>> toCommitList = res -> {
            if (git.failed(res)) {
                LOG.warning(() -> String.format("Failed to obtain the commits from %s.", this));
                return null;
            }

            return Arrays.stream(res.getStdOutTrimmed().split("\\s+")).map(repo::getCommit).map(Optional::get)
                    .collect(Collectors.toList());
        };

        return commitList.map(toCommitList);
    }

    /**
     * Returns a InputStreamReader reading the JSON data return from the GitHub api called with the api path on the current repository
     *
     * @param path
     *         the api path to call
     * @return the InputStreamReader on the result
     */
    Optional<InputStreamReader> getJSONReaderFromURL(String path) {
        URL url;
        InputStreamReader reader;
        try {
            String sep = "?";
            if (path.contains("?")) {
                sep = "&";
            }
            url = new URL(apiBaseURL + path + sep + "access_token=" + oauthToken);
            reader = new InputStreamReader(url.openStream());
        } catch (IOException e) {
            LOG.warning("Could not get data from Github.");
            return Optional.empty();
        }
        return Optional.of(reader);
    }

    /**
     * Gets the Repository for direct access.
     *
     * @return the underlying Repository
     */
    public Repository getRepo() {
        return repo;
    }

    @Override
    public boolean checkout(Reference ref) {
        return repo.checkout(ref);
    }

    @Override
    public boolean forceCheckout(Reference ref) {
        return repo.forceCheckout(ref);
    }

    @Override
    public boolean fetch() {
        return repo.fetch();
    }

    @Override
    public Optional<List<Commit>> getMergeCommits() {
        return repo.getMergeCommits();
    }

    @Override
    public Optional<Commit> getCurrentHEAD() {
        return repo.getCurrentHEAD();
    }

    @Override
    protected Commit getCommitUnchecked(String id) {
        return getCommit(id).get();
    }

    @Override
    public Optional<Commit> getCommit(String id) {
        return repo.getCommit(id);
    }

    @Override
    public Optional<Branch> getBranch(String name) {
        return repo.getBranch(name);
    }

    @Override
    protected Optional<String> toHash(String id) {
        return Optional.empty();
    }

    @Override
    public Optional<Repository> copy(File destination) {
        return repo.copy(destination);
    }

    @Override
    public Optional<List<BlameLine>> blameFile(Path file) {
        return repo.blameFile(file);
    }

    @Override
    public Optional<List<MergeConflict>> blameUnmergedFile(Path file) {
        return repo.blameUnmergedFile(file);
    }

    @Override
    public Optional<Status> getStatus() {
        return repo.getStatus();
    }

    @Override
    public Optional<Status> getStatus(boolean ignored, boolean untracked) {
        return repo.getStatus(ignored, untracked);
    }

    @Override
    public boolean addRemote(String name, String forkURL) {
        return repo.addRemote(name, forkURL);
    }

    @Override
    protected GitWrapper getGit() {
        return git;
    }

    @Override
    public String getUrl() {
        return repo.getUrl();
    }

    @Override
    public File getDir() {
        return dir;
    }

    @Override
    public String getName() {
        return repo.getName();
    }
}
