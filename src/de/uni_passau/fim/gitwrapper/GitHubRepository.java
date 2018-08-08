package de.uni_passau.fim.gitwrapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.processexecutor.ProcessExecutor;
import io.gsonfire.GsonFireBuilder;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A GitHubRepository wraps a (local) Repository to give access to the GitHub API to provide {@link PullRequestData} and
 * {@link IssueData}.
 */
public class GitHubRepository extends Repository {

    private static final Logger LOG = Logger.getLogger(GitHubRepository.class.getCanonicalName());

    private static final Set<Token> tokens = new HashSet<>();
    private static final Queue<Thread> tokenWaitList = new ConcurrentLinkedQueue<>();

    private final Gson gson;
    private final CloseableHttpClient hc;
    private IssueDataProcessor issueProcessor;

    private final Pattern commitPattern = Pattern.compile("([0-9a-f]{40})\n(.*?)\nhash=", Pattern.DOTALL);
    private final String apiBaseURL;
    private List<PullRequest> pullRequests;
    private List<IssueData> issues;
    private Map<String, Commit> unknownCommits = new ConcurrentHashMap<>();
    private Map<String, Optional<Commit>> checkedHashes = new ConcurrentHashMap<>();

    private final AtomicBoolean allowGuessing = new AtomicBoolean(false);
    private final AtomicBoolean sleepOnApiLimit = new AtomicBoolean(true);

    private final ForkJoinPool threadPool;

    /**
     * Create a repository with additional information about GitHub hosted repositories.
     *
     * @param url
     *         the URL of the repository on GitHub
     * @param dir
     *         the directory where the local repository is located
     * @param git
     *         the GitWrapper instance to use
     */
    public GitHubRepository(String url, File dir, GitWrapper git) {
        this(url, dir, git, "");
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories.
     *
     * @param repo
     *         an existing repository cloned from GitHub
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     */
    public GitHubRepository(Repository repo, List<String> oauthToken) {
        this(repo.getUrl(), repo.getDir(), repo.getGit(), oauthToken);
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories.
     *
     * @param url
     *         the URL of the repository on GitHub
     * @param dir
     *         the directory where the local repository is located
     * @param git
     *         the GitWrapper instance to use
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     */
    public GitHubRepository(String url, File dir, GitWrapper git, String oauthToken) {
        this(url, dir, git, Collections.singletonList(oauthToken));
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories. The given file must contain a
     * JSON dump of the list of corresponding issues from GitHub.
     *
     * @param repo
     *         an existing repository cloned from GitHub
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     * @param issueCache
     *         the File containing the issue cache
     * @throws FileNotFoundException
     *         if {@code issueCache} is not found
     * @see #serialize(Object)
     */
    public GitHubRepository(Repository repo, List<String> oauthToken, File issueCache) throws FileNotFoundException {
        this(repo.getUrl(), repo.getDir(), repo.getGit(), oauthToken, issueCache);
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories. The given file must contain a
     * JSON dump of the list of corresponding issues from GitHub.
     *
     * @param url
     *         the URL of the repository on GitHub
     * @param dir
     *         the directory where the local repository is located
     * @param git
     *         the GitWrapper instance to use
     * @param oauthToken
     *         a valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     * @param issueCache
     *         the File containing the issue cache
     * @throws FileNotFoundException
     *         if {@code issueCache} is not found
     * @see #serialize(Object)
     */
    public GitHubRepository(String url, File dir, GitWrapper git, List<String> oauthToken, File issueCache) throws FileNotFoundException {
        this(url, dir, git, oauthToken);

        if (issueProcessor == null) {
            issueProcessor = new IssueDataProcessor(this, apiBaseURL + "/issues/");
        }
        GsonBuilder gb = new GsonFireBuilder().createGsonBuilder();
        gb.registerTypeAdapter(Commit.class, new CommitProcessor(this, new UserDataProcessor(this)));
        gb.registerTypeAdapter(IssueDataCached.class, issueProcessor);
        gb.registerTypeAdapter(ReferencedLink.class, new ReferencedLinkProcessor(this));
        gb.registerTypeAdapter(EventData.class, new EventDataProcessor());
        gb.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimerProcessor());
        gb.setDateFormat("yyyy-MM-dd HH:mm:ss");
        gb.serializeNulls();
        Gson tempGson = gb.create();

        // read cache and fill up missing issue data
        List<IssueData> issues = new ArrayList<>(tempGson.fromJson(new BufferedReader(new FileReader(issueCache)), new TypeToken<List<IssueDataCached>>() {}.getType()));
        issueProcessor.addCache(issues);
        issues.forEach(IssueData::freeze);

        this.issues = issues;
//        getPullRequests();
    }

    /**
     * Create a Repository with additional information about GitHub hosted repositories.
     *
     * @param url
     *         the URL of the repository on GitHub
     * @param dir
     *         the directory where the local repository is located
     * @param git
     *         the GitWrapper instance to use
     * @param oauthToken
     *         a list of valid oAuth token for GitHub
     *         (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *         information about creating such tokens)
     */
    public GitHubRepository(String url, File dir, GitWrapper git, List<String> oauthToken) {
        super(git, url, dir);
        if (url.contains("git@")) {
            url = url.replace(":", "/").replace("git@", "https://");
        }
        apiBaseURL = url.replace(".git", "").replace("//github.com/", "//api.github.com/repos/");
        LOG.fine(String.format("Creating repo for %s", apiBaseURL));

        synchronized (tokens) {
            oauthToken.stream().map(Token::new).forEach(tokens::add);
        }

        if (issueProcessor == null) {
            issueProcessor = new IssueDataProcessor(this, apiBaseURL + "/issues/");
        }
        GsonFireBuilder gfb = new GsonFireBuilder();
        UserDataProcessor userProcessor = new UserDataProcessor(this);
        ReferencedLinkProcessor referencedLinkProcessor = new ReferencedLinkProcessor(this);
        gfb.registerPostProcessor(IssueData.class, issueProcessor);
        gfb.registerPostProcessor(ReferencedLink.class, referencedLinkProcessor);
        gfb.registerPostProcessor(EventData.ReferencedEventData.class, new EventDataProcessor.ReferencedEventProcessor(this));
        gfb.registerPostProcessor(EventData.LabeledEventData.class, new EventDataProcessor.LabeledEventProcessor());
        GsonBuilder gb = gfb.createGsonBuilder();
        gb.registerTypeAdapter(Commit.class, new CommitProcessor(this, userProcessor));
        gb.registerTypeAdapter(IssueDataCached.class, issueProcessor);
        gb.registerTypeAdapter(ReferencedLink.class, referencedLinkProcessor);
        gb.registerTypeAdapter(EventData.class, new EventDataProcessor());
        gb.registerTypeAdapter(UserData.class, userProcessor);
        gb.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimerProcessor());
        gb.setDateFormat("yyyy-MM-dd HH:mm:ss");
        gb.serializeNulls();
        gson = gb.create();

        hc = HttpClients.createDefault();

        threadPool = new ForkJoinPool(oauthToken.size());
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
     *         if {@code true}, will include {@link PullRequestData PullRequests} as well
     * @return optionally a List of IssueData or an empty Optional if an error occurred
     */
    public Optional<List<IssueData>> getIssues(boolean includePullRequests) {
        return getIssues(includePullRequests, null);
    }

    /**
     * Gets a List of Issues. Use the {@code updateSince} parameter to request updates (to cached data). Please note,
     * that this will not remove data deleted at GitHub.
     *
     * @param includePullRequests
     *         if {@code true}, will include {@link PullRequestData PullRequests} as well
     * @param updateSince
     *         if not {@code null}, will update all issues added or with changes since the given date
     * @return optionally a List of IssueData or an empty Optional if an error occurred
     */
    public Optional<List<IssueData>> getIssues(boolean includePullRequests, OffsetDateTime updateSince) {
        if (issues == null || updateSince != null) {
            String timeLimit;
            Type type = new TypeToken<IssueDataCached>() {}.getType();
            if (updateSince != null) {
                timeLimit = "&since=" + updateSince.format(DateTimeFormatter.ISO_DATE_TIME);
                type = new TypeToken<IssueData>() {}.getType();
            }
            else timeLimit = "";
            Type finalType = type;
            getJSONStringFromPath("/issues?state=all" + timeLimit).map(json -> {
                List<IssueData> data;
                try {
                    ArrayList<JsonElement> list = gson.fromJson(json, new TypeToken<ArrayList<JsonElement>>() {}.getType());
                    Callable<List<IssueData>> converter = () ->
                            list.parallelStream()
                                    .map(element -> (IssueData) (gson.fromJson(element, finalType)))
                                    .collect(Collectors.toList());
                    data = threadPool.submit(converter).join();

                    // freeze the issues
                    threadPool.submit(() -> data.parallelStream().forEach(IssueData::freeze));

                } catch (JsonSyntaxException e) {
                    LOG.warning("Encountered invalid JSON: " + json);
                    return null;
                }
                return data;
            }).ifPresent(list -> {
                Set<IssueData> all = new HashSet<>(list);
                all.addAll(issueProcessor.getCache().values());
                list = new ArrayList<>(all);
                list.sort(Comparator.comparing(issue -> issue.created_at));
                issues = Collections.unmodifiableList(list);
            });
        }

        if (!includePullRequests) {
            return Optional.of(issues.stream().filter(issueData -> !issueData.isPullRequest).collect(Collectors.toList()));
        }
        return Optional.of(issues);
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
    Optional<List<ReferencedLink<String>>> getComments(IssueData issue) {
        return getJSONStringFromPath("/issues/" + issue.number + "/comments?state=all").map(json -> {
            try {
                return gson.fromJson(json, new TypeToken<ArrayList<ReferencedLink>>() {}.getType());
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
        getIssues(true).ifPresent(issues -> {
            Callable<List<PullRequest>> converter = () -> issues.parallelStream().filter(x -> x.isPullRequest).map(x -> (PullRequestData) x).map(pr -> {
                State state = State.getPRState(pr.state, pr.merged_at != null);

                // if the fork was deleted and the PR was rejected or is still open, we cannot get verify the
                // commits, so the PR is dropped
                if (pr.head.repo == null && State.includes(state, State.UNMERGED)) {
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
                if ( /* TODO super ?*/ !super.getCommit(pr.head.sha).isPresent()) {
                    LOG.warning(String.format("The history of the repo does not include the merged PR %d, therefore it was dropped!", pr.number));
                    return null;
                }

                Reference target = getBranch("origin/" + pr.base.ref).orElse(null);

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
                    return new PullRequest(this, State.MERGED, target, commits, pr);
                }
                return new PullRequest(this, state, target, commits, pr);
            }).filter(Objects::nonNull).sorted(Comparator.comparing(pr -> pr.getIssue().created_at)).collect(Collectors.toList());
            pullRequests = threadPool.submit(converter).join();
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
        Optional<ProcessExecutor.ExecRes> commitList = getGit().exec(getDir(), "log", params.toArray(new String[0]));
        Function<ProcessExecutor.ExecRes, List<Commit>> toCommitList = res -> {
            if (getGit().failed(res)) {
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
     * Returns a string reading the JSON data returned from the GitHub API called with the API path on the current
     * repository.
     *
     * @param path
     *         the API path to call
     * @return optionally a string representing the JSON result, or an empty Optional, if the call failed
     */
    private Optional<String> getJSONStringFromPath(String path) {
        return getJSONStringFromURL(apiBaseURL + path);
    }

    /**
     * Returns a string of the JSON data returned from the GitHub API called with the given URL.
     * The caller is responsible, that the URL matches this repository.
     *
     * @param urlString
     *         the URL to call
     * @return optionally, a string representing the JSON result, or an empty Optional, if the call failed
     */
    Optional<String> getJSONStringFromURL(String urlString) {
        String json;
        LOG.fine(String.format("Getting json from %s by Thread %s", urlString, Thread.currentThread().getName()));
        try {
            List<String> data = new ArrayList<>();
            String url = urlString + (urlString.contains("?") ? "&" : "?") + "per_page=100";

            Token token = null;
            try {
                Optional<Token> optToken = getValidToken();
                if (!optToken.isPresent()) {
                    LOG.warning("No token available");
                    return Optional.empty();
                }
                token = optToken.get();

                do {
                    if (!token.getToken().isPresent()) {
                        LOG.warning(String.format("Tried to use token %s not held by thread %s", token, Thread.currentThread()));
                        // return Optional.empty();
                    }

                    String httpURL = url + (token.getToken().get().isEmpty() ? "" : "&access_token=" + token.getToken().get());
                    LOG.info("Querying URL: " + httpURL);
                    CloseableHttpResponse resp = hc.execute(new HttpGet(httpURL));

                    Map<String, List<String>> headers = Arrays.stream(resp.getAllHeaders())
                            .collect(Collectors.toMap(Header::getName,
                                    h -> new ArrayList<>(Collections.singletonList(h.getValue())),
                                    (a, b) -> {a.addAll(b); return a;}));

                    int rateLimitRemaining = Integer.parseInt(headers.getOrDefault("X-RateLimit-Remaining", Collections.singletonList("0")).get(0));
                    Instant rateLimitReset = Instant.ofEpochMilli(Long.parseLong(headers.getOrDefault("X-RateLimit-Reset", Collections.singletonList("0")).get(0)) * 1000);
                    token.update(rateLimitRemaining, rateLimitReset);

                    // if this call could have been the last possible, fetch a new token for the next round
                    if (!token.isUsable()) {
                        releaseToken(token);
                        optToken = getValidToken();
                        if (!optToken.isPresent()) {
                            LOG.warning("No token available");
                            token = null;
                            resp.close();
                            return Optional.empty();
                        }
                        token = optToken.get();
                    }

                    if (resp.getStatusLine().getStatusCode() != 200) {
                        LOG.warning(String.format("Could not access api method: %s returned %s", url, resp.getStatusLine()));
                        resp.close();

                        // probably in rate limit, lets try again with the new token
                        if (resp.getStatusLine().getStatusCode() == 403)
                            continue;

                        return Optional.empty();
                    }

                    // read content
                    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(resp.getEntity().getContent()))) {
                        data.add(buffer.lines().collect(Collectors.joining("\n")));
                    }

                    // check, if another page is available
                    Optional<String> next = Arrays.stream(headers.getOrDefault("Link",
                            new ArrayList<>(Collections.singleton(""))
                    ).get(0).split(","))
                            .filter(link -> link.contains("next")).findFirst();
                    resp.close();

                    if (!next.isPresent()) break;
                    String nextUrl = next.get();
                    url = nextUrl.substring(nextUrl.indexOf("<") + 1, nextUrl.indexOf(">"));
                } while (true);

            } finally {
                if (token != null) releaseToken(token);
            }

            // concatenate all results together, making one large JSON string
            json = String.join("", data).replace("][", ",");

        } catch (IOException e) {
            LOG.warning("Could not get data from GitHub.");
            return Optional.empty();
        }

        return json.isEmpty() ? Optional.empty() : Optional.of(json);
    }

    /**
     * Gets a valid API Token.
     * If waiting for the reset of an exhausted token is allowed, this call will block until the first token with free
     * calls is available.
     * This method will {@link Token#acquire() acquire} the lock on the Token, if one is returned, the caller is
     * responsible for releasing the lock, once he does not need it any more.
     *
     * @return a valid token, or an empty Optional if none is found and waiting is not allowed.
     * @see #sleepOnApiLimit(boolean)
     * @see #releaseToken(Token)
     */
    private Optional<Token> getValidToken() {
        synchronized (tokens) {
            // short-circuit if we already have a valid token
            Optional<Token> optToken = tokens.stream().filter(Token::isHeld).filter(Token::isValid).findFirst();
            if (optToken.isPresent()) {
                optToken.get().acquire();
                return optToken;
            }

            // acquire a token or, if allowed wait until one becomes available
            optToken = tokens.stream().filter(Token::isValid).filter(Token::acquire).findFirst();
            if (sleepOnApiLimit() && !optToken.isPresent()) {
                try {
                    LOG.info(String.format("Waiting until %s before the next token is available.", getTokenResetTime()));
                    tokenWaitList.add(Thread.currentThread());
                    Thread.sleep(getTokenResetTime().minusMillis(System.currentTimeMillis()).toEpochMilli());
                } catch (InterruptedException e) {
                    return getValidToken();
                }
                return getValidToken();
            }
            optToken.ifPresent(token -> LOG.finest(Thread.currentThread() + " acquired token " + token));
            return optToken;
        }
    }

    /**
     * {@link Token#release() Releases} the Token and informs the next one waiting.
     *
     * @param token
     *         the Token to release
     */
    private void releaseToken(Token token) {
        token.release();
        LOG.finest(Thread.currentThread() + " released token " + token);
        Thread next = tokenWaitList.poll();
        if (next != null) {
            LOG.fine("Waking up " + next + " waiting on token");
            next.interrupt();
        }
    }

    /**
     * Gets the earliest time, any of the active tokens can be used again.
     * If there are valid tokens but all are locked, this will return {@code now + 10 seconds}.
     *
     * @return the earliest time a single API call can succeed
     */
    public static Instant getTokenResetTime() {
        synchronized (tokens) {
            if (tokens.stream().anyMatch(Token::isUsable)) return Instant.now();
            if (tokens.stream().anyMatch(Token::isValid))  return Instant.now().plusSeconds(10);
            return tokens.stream().map(Token::getResetTime).min(Comparator.naturalOrder()).get();
        }
    }

    /**
     * Gets, if the execution is waiting on an API token to becomes available if all tokens are exhausted.
     *
     * @return {@code true} if a sleeping is requested.
     * @see #sleepOnApiLimit(boolean)
     */
    private boolean sleepOnApiLimit() {
        synchronized (sleepOnApiLimit) {
            return sleepOnApiLimit.get();
        }
    }

    /**
     * Setter for toggling waiting on exhausted API rate limit.
     * Default is {@code true}.
     * This is a global switch and takes immediate effect on all running and future requests.
     *
     * @param sleepOnApiLimit
     *         if {@code true}, all API calls will wait until the call blocked due to rate limiting succeeds again
     * @see #getTokenResetTime()
     */
    public void sleepOnApiLimit(boolean sleepOnApiLimit) {
        synchronized (this.sleepOnApiLimit) {
            this.sleepOnApiLimit.set(sleepOnApiLimit);
        }
    }

    /**
     * Gets, if strict email determination is required.
     *
     * @return {@code true} if guessing of user email is allowed
     * @see #allowGuessing(boolean)
     */
    boolean allowGuessing() {
        synchronized (allowGuessing) {
            return allowGuessing.get();
        }
    }

    /**
     * Setter for toggling strict email determination method.
     * Default is {@code false}.
     * This is a global switch and takes immediate effect on all running and future requests.
     *
     * @param guess
     *         if {@code true}, guessing of user email is allowed
     * @see #allowGuessing()
     */
    public void allowGuessing(boolean guess) {
        synchronized (allowGuessing) {
            allowGuessing.set(guess);
        }
    }

    /**
     * This method provides a convenient way to convert GitHub-related objects back to their JSON representation
     * (For now only GitHub related data and commits can be serialized)
     *
     * @param obj
     *         the object to serialize
     * @return a String containing the JSON representation
     */
    public <T> String serialize(T obj) {
        return gson.toJson(obj);
    }

    /**
     * Gets the corresponding Commit for the given sha1 hash. If the commit is not known by the local repository, a
     * query is sent to GitHub, to confirm its existence there and additional author data is retrieved. (e.g. GitHub
     * retains a copy, even if a force push is performed).
     *
     * @param hash
     *         the sha1 hash of the Commit
     * @return optionally a Commit, or an empty Optional, if neither the local repository nor GitHub have a reference
     * to a Commit with the given hash
     */
    Optional<Commit> getGithubCommit(String hash) {
        return checkedHashes.computeIfAbsent(hash, x ->
                getJSONStringFromURL(apiBaseURL + "/commits/" + hash).map(commitInfo ->
                        gson.fromJson(commitInfo, new TypeToken<Commit>() {}.getType())));
    }

    /**
     * Creates a new Commit with the given data, and tries to fill in the  missing data from the local Repository
     *
     * @param hash
     *         the sha1 hash of the Commit
     * @param message
     *         the commit messages
     * @param author
     *         Data about the Commit author
     * @return the new Commit
     */
    Commit getReferencedCommit(String hash, String message, UserData.CommitUserData author) {
        // Disable logging for this method call, so false positives don't reported
        Logger repoLog = Logger.getLogger(Repository.class.getCanonicalName());
        Logger commitLog = Logger.getLogger(Commit.class.getCanonicalName());
        Level repoLevel = repoLog.getLevel();
        Level commitLevel = commitLog.getLevel();
        repoLog.setLevel(Level.OFF);
        commitLog.setLevel(Level.OFF);

        Commit commit = getCommitUnchecked(hash);

        // init and check if data present, otherwise init manually
        if (commit.getAuthor() == null) {
            commit.setAuthor(author.name);
            commit.setAuthorMail(author.email);
            commit.setAuthorTime(author.date);
            commit.setMessage(message);
        }

        // Reset logging
        repoLog.setLevel(repoLevel);
        commitLog.setLevel(commitLevel);

        return commit;
    }

    @Override
    Commit getCommitUnchecked(String id) {
        return getCommit(id).orElse(unknownCommits.computeIfAbsent(id, (key) -> new Commit(this, key)));
    }

    @Override
    public Optional<Commit> getCommit(String id) {
        return Optional.ofNullable(super.getCommit(id).orElse(unknownCommits.get(id)));
    }

    /**
     * Gets a PullRequest by its full qualified branch name.
     *
     * @param name
     *         the branch name
     * @return optionally the PullRequest, if it is known
     */
    public Optional<PullRequest> getPullRequest(String name) {
        if (pullRequests != null) {
            return pullRequests.stream().filter(pr -> ((PullRequestData) pr.getIssue()).branch.equals(name)).findFirst();
        } else {
            return issues.stream().filter(IssueData::isPullRequest).map(pr -> ((PullRequestData) pr))
                    .filter(pr -> pr.branch.equals(name)).findFirst().map(prd ->
                            new PullRequest(this, State.getPRState(prd.state, prd.merged_at != null),
                                    getBranch("origin/" + prd.base.ref).orElse(null), /*TODO*/ null, prd));
        }
    }

    IssueData getIssueFromCache(Integer target) {
        return issueProcessor.getCache().get(target);
    }

    /**
     * Only used internally for deserialization, don't implement!
     */
    interface IssueDataCached {}
}
