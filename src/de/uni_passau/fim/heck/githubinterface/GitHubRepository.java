package de.uni_passau.fim.heck.githubinterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import de.uni_passau.fim.heck.githubinterface.datadefinitions.RefData;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.State;
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
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

/**
 * A GitHubRepository wraps a (local) Repository to give access to the GitHub API to provide {@link PullRequestData} and
 * {@link IssueData}.
 */
public class GitHubRepository extends Repository {

    private static final Logger LOG = Logger.getLogger(GitHubRepository.class.getCanonicalName());

    private final Repository repo;
    private final Gson gson;
    private final HttpClient hc;

    private final String apiBaseURL;
    private final String oauthToken;
    private final GitWrapper git;
    private final File dir;

    private List<PullRequest> pullRequests;
    private boolean allowGuessing;

    /**
     * Create a wrapper around a (local) repository with additional information about GitHub hosted repositories.
     *
     * @param repo
     *         the local Repository
     * @param git
     *         the GitWrapper instance to use
     */
    public GitHubRepository(Repository repo, GitWrapper git) {
        this(repo, git, "");
    }

    /**
     * Create a wrapper around a (local) Repository with additional information about GitHub hosted repositories.
     *
     * @param repo
     *         the local repository
     * @param git
     *         the GitWrapper instance to use
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     */
    public GitHubRepository(Repository repo, GitWrapper git, String oauthToken) {
        this.repo = repo;
        String repoUrl = repo.getUrl();
        if (repoUrl.contains("git@")) {
            repoUrl = repoUrl.replace(":", "/").replace("git@", "https://");
        }
        apiBaseURL = repoUrl.replace(".git", "").replace("//github.com/", "//api.github.com/repos/");
        LOG.fine(String.format("Creating repo for %s", apiBaseURL));
        this.git = git;
        dir = repo.getDir();
        this.oauthToken = oauthToken;

        GsonFireBuilder gfb = new GsonFireBuilder();
        gfb.registerPostProcessor(IssueData.class, new IssueDataPostprocessor(this));
        GsonBuilder gb = gfb.createGsonBuilder();
        gb.registerTypeAdapter(Commit.class, new CommitSerializer());
        gb.registerTypeAdapter(UserData.class, new UserDataDeserializer(this));
        gb.registerTypeAdapter(EventData.class, new EventDataDeserializer());
        gb.setDateFormat("yyyy-MM-dd HH:mm:ss");
        gb.serializeNulls();
        gson = gb.create();

        hc = HttpClients.createDefault();
    }

    /**
     * Gets a List of PullRequests.
     *
     * @param filter
     *         The state of the PullRequests to include (see {@link State#includes(State, State)})
     * @return optionally a List of PullRequests or an empty Optional, if an error occurred
     */
    public Optional<List<PullRequest>> getPullRequests(State filter) {
        getPullRequests();
        if (pullRequests == null) {
            LOG.warning("Could not get PRs.");
            return Optional.empty();
        }
        return Optional.of(pullRequests.stream().filter(pr -> State.includes(pr.getState(), filter)).collect(Collectors.toList()));
    }

    /**
     * Gets a List of Issues.
     *
     * @param includePullRequests
     *         if {@code true}, will include {@link PullRequest PullRequests} as well
     * @return optionally a List of IssueData or an empty Optional if an error occurred
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

            if (data != null && !includePullRequests) {
                return data.stream().filter(issueData -> !issueData.isPullRequest).collect(Collectors.toList());
            }
            return data;
        });
    }

    /**
     * Returns a List of Events for an Issue.
     *
     * @param issue
     *         the parent IssueData
     * @return optionally a List of EventData or an empty Optional if an error occurred
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
     * Returns a List of Comments for an Issue.
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
     * Gets a List of all Commits before a given Date.
     *
     * @param date
     *         the Date until Commits are included
     * @return optionally a List of Commits or an empty Optional if the operation failed
     */
    public Optional<List<Commit>> getCommitsBeforeDate(Date date) {
        return getCommitsInRange(null, date, "*", false);
    }

    /**
     * Gets a List of all Commits before a given Date.
     *
     * @param date
     *         the Date until Commits are included
     * @param branch
     *         limit Commits to this specific branch
     * @return optionally a List of Commits or an empty Optional if the operation failed
     */
    public Optional<List<Commit>> getCommitsBeforeDate(Date date, String branch) {
        return getCommitsInRange(null, date, branch, false);
    }

    /**
     * Gets a list of merge commits between the two provided times.
     *
     * @param start
     *         the timestamp, after which the first commit is included
     * @param end
     *         the timestamp after the last included commit
     * @return optionally a List of Commits, or an empty Optional if an error occurred
     */
    public Optional<List<Commit>> getMergeCommitsBetween(Date start, Date end) {
        return getCommitsInRange(start, end, "*", true);
    }

    /**
     * Gets a list of merge commits reachable from {@code end} and in the history of {@code start}.
     *
     * @param start
     *         the first commit to include
     * @param end
     *         the last commit to include
     * @return optionally a List of Commits, or an empty Optional if an error occurred
     */
    public Optional<List<Commit>> getMergeCommitsBetween(Commit start, Commit end) {
        return getMergeCommits().map(list -> list.stream()
                .filter(c -> start == null || c.equals(start) || c.checkAncestry(start).orElse(false))
                .filter(c -> end == null || c.equals(end) || end.checkAncestry(c).orElse(true))
                .collect(Collectors.toList()));
    }

    /**
     * Gets the list of pull requests from GitHub, if it is not already cached.
     */
    private void getPullRequests() {
        if (pullRequests != null) {
            LOG.fine("Using cached list of PRs");
            return;
        }
        LOG.fine("Building new list of PRs");
        getJSONStringFromPath("/pulls?state=all").ifPresent(json -> {
            ArrayList<PullRequestData> data;
            try {
                data = gson.fromJson(json, new TypeToken<ArrayList<PullRequestData>>() {}.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return;
            }
            pullRequests = new ArrayList<>(data.stream().map(pr -> {
                State state = State.getPRState(pr.state, pr.merged_at != null);

                // if the fork was deleted and the PR was rejected or is still open, we cannot get verify the
                // commits, so the PR is dropped
                if (pr.head.repo == null && (state == State.DECLINED || state == State.OPEN)) {
                    LOG.warning(String.format("PR %d has no fork repo and was not merged, therefore it was dropped!", pr.number));
                    return null;
                }

                // if the source branch on the fork was deleted and the PR was declined we also cannot get verify
                // the commits, so the PR is dropped as well
                if (pr.head.repo != null &&
                        !addRemote(pr.head.repo.full_name, pr.head.repo.clone_url) &&
                        !getBranch(pr.head.repo.full_name + "/" + pr.head.ref).isPresent()) {
                    LOG.warning(String.format("The source branch of PR %d was deleted and the PR was not merged, therefore it was dropped!", pr.number));
                    return null;
                }

                // we still can't find the tip, this probably means the history was rewritten and the refs are invalid
                // nothing we can do but drop the PR
                if (!repo.getCommit(pr.head.sha).isPresent()) {
                    LOG.warning(String.format("The history of the repo does not include the merged PR %d, therefore it was dropped!", pr.number));
                    return null;
                }

                Reference target = repo.getBranch("origin/" + pr.base.ref).orElse(null);

                Optional<String> commitData = getJSONStringFromPath("/pulls/" + pr.number + "/commits");
                //noinspection unchecked
                List<Commit> commits = commitData.map(cd ->
                        ((ArrayList<RefData>) gson.fromJson(cd, new TypeToken<ArrayList<RefData>>() {}.getType())).stream().map(c ->
                                getCommit(c.sha).orElseGet(() -> {
                                    LOG.warning(String.format("Invalid commit %s from PR %d", c.sha, pr.number));
                                    return null;
                                }))
                            .filter(Objects::nonNull).collect(Collectors.toList()))
                    .orElseGet(() -> {
                        LOG.warning(String.format("Could not get commits for PR %d", pr.number));
                        return Collections.emptyList();
                    });

                if (pr.head.repo == null) {
                    LOG.warning(String.format("PR %d has no fork repo", pr.number));
                    return new PullRequest(this, target, commits, pr);
                }
                return new PullRequest(this, pr.head.ref, pr.head.repo.full_name, state, target, commits, pr);

            }).filter(Objects::nonNull).collect(Collectors.toList()));
        });
    }

    /**
     * Gets a List of all Commits before a given Date on a branch.
     *
     * @param start
     *         the Date since which Commits are included
     * @param end
     *         the Date until Commits are included
     * @param branch
     *         limit Commits to this specific branch
     * @param onlyMerges
     *         if {@code true} only merge Commits are included
     * @return optionally a List of Commits or an empty optional if the operation failed
     */
    private Optional<List<Commit>> getCommitsInRange(Date start, Date end, String branch, boolean onlyMerges) {
        LOG.fine(String.format("Getting %s between %Tc and %Tc on %s", onlyMerges ? "merges" : "commits", start, end, branch));
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        ArrayList<String> params = new ArrayList<>(Arrays.asList("--format=tformat:%H", "--branches=" + branch));
        if (end != null) params.add("--until=" + df.format(end));
        if (start != null) params.add("--since=" + df.format(start));
        if (onlyMerges) params.add("--merges");
        Optional<ProcessExecutor.ExecRes> commitList = git.exec(dir, "log", params.toArray(new String[0]));
        Function<ProcessExecutor.ExecRes, List<Commit>> toCommitList = res -> {
            if (git.failed(res)) {
                LOG.warning(() -> String.format("Failed to obtain the commits from %s.", this));
                return null;
            }

            if (res.getStdOutTrimmed().isEmpty()) {
                return new ArrayList<>();
            }

            return Arrays.stream(res.getStdOutTrimmed().split("\\s+")).map(this::getCommitUnchecked).collect(Collectors.toList());
        };

        return commitList.map(toCommitList);
    }

    /**
     * Returns an InputStreamReader reading the JSON data returned from the GitHub API called with the API path on the
     * current repository.
     *
     * @param path
     *         the API path to call
     * @return an InputStreamReader on the result
     */
    private Optional<String> getJSONStringFromPath(String path) {
        return getJSONStringFromURL(apiBaseURL + path);
    }

    /**
     * Returns an InputStreamReader reading the JSON data returned from the GitHub API called with the given URL.
     * The caller is responsible, that the URL matches this repository.
     *
     * @param urlString
     *         the URL to call
     * @return an InputStreamReader on the result
     */
    Optional<String> getJSONStringFromURL(String urlString) {
        String url;
        String json;
        LOG.fine(String.format("Getting json from %s", urlString));
        try {
            String sep = "?";
            if (urlString.contains("?")) sep = "&";
            String count = "&per_page=100";

            List<String> data = new ArrayList<>();
            url = urlString + sep + "access_token=" + oauthToken + count;

            do {
                HttpResponse resp = hc.execute(new HttpGet(url));

                Map<String, List<String>> headers = Arrays.stream(resp.getAllHeaders())
                        .collect(Collectors.toMap(Header::getName,
                                h -> new ArrayList<>(Collections.singletonList(h.getValue())),
                                (a, b) -> {a.addAll(b); return a;}));

                boolean noAPICallsRemaining = headers.getOrDefault("X-RateLimit-Remaining",
                        new ArrayList<>(Collections.singleton("0"))).stream().anyMatch(x -> x.equals("0"));
                if (noAPICallsRemaining) {
                    Date timeout = new Date(Long.parseLong(headers.get("X-RateLimit-Reset").get(0)) * 1000);
                    LOG.warning("Reached rate limit, try again at " + timeout);
                    break;
                }

                Optional<String> next = Arrays.stream(headers.getOrDefault("Link",
                            new ArrayList<>(Collections.singleton(""))
                        ).get(0).split(","))
                    .filter(link -> link.contains("next")).findFirst();
                try (BufferedReader buffer = new BufferedReader(new InputStreamReader(resp.getEntity().getContent()))) {
                    data.add(buffer.lines().collect(Collectors.joining("\n")));
                }

                if (!next.isPresent()) break;
                String nextUrl = next.get();
                url = nextUrl.substring(nextUrl.indexOf("<") + 1, nextUrl.indexOf(">"));
            } while (true);

            // concatenate all results together, making one large JSON string
           json = String.join("", data).replace("][", ",");

        } catch (IOException e) {
            LOG.warning("Could not get data from GitHub.");
            return Optional.empty();
        }
        return json == null || json.isEmpty() ? Optional.empty() : Optional.of(json);
    }

    /**
     * Cleans up the working directory.
     *
     * @return {@code true} if successful
     */
    public boolean cleanup() {
        Optional<ProcessExecutor.ExecRes> result = git.exec(dir, "clean", "-d", "-x", "-f");
        Function<ProcessExecutor.ExecRes, Boolean> toBoolean = res -> {
            if (git.failed(res)) {
                LOG.warning("Failed to clean directory");
                return false;
            }
            return true;
        };

        return result.map(toBoolean).orElse(false);
    }

    /**
     * Gets, if strict email determination is required.
     *
     * @return {@code true} if guessing of user email is allowed
     * @see #allowGuessing(boolean)
     */
    boolean allowGuessing() {
        return allowGuessing;
    }

    /**
     * Setter for toggling strict email determination method.
     *
     * @param guess
     *         if {@code true}, guessing of user email is allowed
     * @see #allowGuessing()
     */
    public void allowGuessing(boolean guess) {
        allowGuessing = guess;
    }

    /**
     * This method provides a convenient way to convert GitHub-related objects back to their JSON representation
     * (For now only GitHub related data and commits can be serialized)
     *
     * @param obj
     *         the object to serialize
     * @return a String containing the JSON representation
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
