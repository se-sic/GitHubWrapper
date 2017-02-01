package de.uni_passau.fim.heck.githubinterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.CommentData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.IssueData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.PullRequestData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.UserData;
import de.uni_passau.fim.seibt.gitwrapper.process.ProcessExecutor;
import de.uni_passau.fim.seibt.gitwrapper.repo.BlameLine;
import de.uni_passau.fim.seibt.gitwrapper.repo.Branch;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;
import de.uni_passau.fim.seibt.gitwrapper.repo.GitWrapper;
import de.uni_passau.fim.seibt.gitwrapper.repo.MergeConflict;
import de.uni_passau.fim.seibt.gitwrapper.repo.Reference;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;
import de.uni_passau.fim.seibt.gitwrapper.repo.Status;
import io.gsonfire.GsonFireBuilder;

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
    public GitHubRepository(Repository repo, GitWrapper git) {
        this(repo, git, "");
    }

    /**
     * Create a wrapper around a (local) repository with additional information about Github hosted repositories.
     *
     * @param repo
     *         the local repository
     * @param git
     *         the GitWrapper instance to use
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see https://github.com/settings/tokens for information about creating such tokens)
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

        GsonFireBuilder gfb = new GsonFireBuilder();
        gfb.registerPostProcessor(IssueData.class, new IssueDataPostprocessor(this));
        GsonBuilder gb = gfb.createGsonBuilder();
        gb.registerTypeAdapter(Commit.class, new CommitSerializer());
        gb.registerTypeAdapter(UserData.class, new UserDataDeserializer(this));
        gb.registerTypeAdapter(EventData.class, new EventDataDeserializer());
        gson = gb.create();
    }

    /**
     * Gets a list of all PullRequests.
     *
     * @param onlyOpen
     *         if <code>true</code>, only open pull requests are included
     * @return optionally a list of PullRequests or an empty Optional, if an error occurred
     */
    public Optional<List<PullRequest>> getPullRequests(boolean onlyOpen) {
        return getJSONStringFromPath("/pulls?state=all").map(json -> {
            ArrayList<PullRequestData> data;
            try {
                data = gson.fromJson(json, new TypeToken<ArrayList<PullRequestData>>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
            return data.stream().filter(pr -> !(pr.state.equals("closed") && onlyOpen)).map(pr ->
                    new PullRequest(this, pr.head.ref, pr.head.repo.full_name,
                            pr.head.repo.html_url, pr.state, repo.getBranch(pr.base.ref).get(), pr)
            ).collect(Collectors.toList());
        });
    }

    /**
     * Gets a list of Issues.
     *
     * @param includePullRequests
     *         if <code>true</code>, will include pull requests as well
     * @return optionally a list of IssueData or an empty Optional if an error occurred
     */
    public Optional<List<IssueData>> getIssues(boolean includePullRequests) {
        return getJSONStringFromPath("/issues?state=all").map(json -> {
            ArrayList<IssueData> data;
            try {
                data = gson.fromJson(json, new TypeToken<ArrayList<IssueData>>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
            if (includePullRequests || data == null) {
                return data;
            } else {
                return data.stream().filter(issueData -> !issueData.isPullRequest).collect(Collectors.toList());
            }
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
        return getJSONStringFromPath("/issues/" + issue.number + "/events").map(json -> {
            try {
                return gson.fromJson(json, new TypeToken<ArrayList<EventData>>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
        });
    }

    /**
     * Returns a list of comments for an Issue
     *
     * @param issue
     *         the parent IssueData
     * @return optionally a list of CommentData or an empty Optional if an error occurred
     */
    Optional<List<CommentData>> getComments(IssueData issue) {
        return getJSONStringFromPath("/issues/" + issue.number + "/comments?state=all").map(json -> {
            try {
                return gson.fromJson(json, new TypeToken<ArrayList<CommentData>>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
        });
    }

    /**
     * Gets a list of all commits before a given date.
     *
     * @param date
     *         the date until commits are included
     * @return optionally a list of {@link Commit commits} or an empty optional if the operation failed
     */
    Optional<List<Commit>> getCommitsBeforeDate(Date date) {
        return getCommitsBeforeDate(date, "*");
    }

    /**
     * Gets a list of all commits before a given date on a branch.
     *
     * @param date
     *         the date until commits are included
     * @param branch
     *         limit commits to this specific branch
     * @return optionally a list of {@link Commit commits} or an empty optional if the operation failed
     */
    Optional<List<Commit>> getCommitsBeforeDate(Date date, String branch) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Optional<ProcessExecutor.ExecRes> commitList = git.exec(dir, "log", "--format=tformat:%H", "--branches=" + branch, "--until=" + df.format(date));
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
     * Returns an InputStreamReader reading the JSON data return from the GitHub api called with the api path on the current repository.
     *
     * @param path
     *         the api path to call
     * @return the InputStreamReader on the result
     */
    Optional<String> getJSONStringFromPath(String path) {
        return getJSONStringFromURL(apiBaseURL + path);
    }

    /**
     * Returns a InputStreamReader reading the JSON data return from the GitHub api called with the url.
     * The caller is responsible, that the url matches this repository.
     *
     * @param urlString
     *         the url to call
     * @return the InputStreamReader on the result
     */
    Optional<String> getJSONStringFromURL(String urlString) {
        URL url;
        String json;
        try {
            String sep = "?";
            if (urlString.contains("?")) sep = "&";
            String count = "&per_page=100";

            List<InputStream> dataStreams = new ArrayList<>();
            url = new URL(urlString + sep + "access_token=" + oauthToken + count);

            do {
                URLConnection conn = url.openConnection();
                Map<String, List<String>> headers = conn.getHeaderFields();

                boolean noAPICallsRemaining = headers.getOrDefault("X-RateLimit-Remaining",
                        new ArrayList<String>() {{ add("0"); }}).stream().anyMatch(x -> x.equals("0"));
                if (noAPICallsRemaining) {
                    Date timeout = new Date(Long.parseLong(headers.get("X-RateLimit-Reset").get(0)) * 1000);
                    LOG.warning("Reached rate limit, try again at " + timeout);
                    break;
                }

                Optional<String> next = Arrays.stream(headers.getOrDefault("Link",
                        new ArrayList<String>() {{ add(""); }}
                    ).get(0).split(","))
                        .filter(link -> link.contains("next")).findFirst();
                dataStreams.add(url.openStream());

                if (!next.isPresent()) break;
                String nextUrl = next.get();
                url = new URL(nextUrl.substring(nextUrl.indexOf("<") + 1, nextUrl.indexOf(">")));
            } while (true);

            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(new SequenceInputStream(Collections.enumeration(dataStreams))))) {
                json = buffer.lines().collect(Collectors.joining("\n")).replace("][", ",");
            }
        } catch (IOException e) {
            LOG.warning("Could not get data from Github.");
            return Optional.empty();
        }
        return Optional.of(json);
    }

    /**
     * This method provides a convenient was to convert GitHub-related objects back to their JSON representation
     *
     * @param obj
     *         the object to serialize
     * @return a string canting the JSON representation
     */
    public String serialize(Object obj) {
        return gson.toJson(obj);
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
