package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.*;
import de.uni_passau.fim.seibt.gitwrapper.process.ProcessExecutor;
import de.uni_passau.fim.seibt.gitwrapper.repo.*;
import io.gsonfire.GsonFireBuilder;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A GitHubRepository wraps a (local) Repository to give access to the GitHub API to provide {@link PullRequestData} and
 * {@link IssueData}.
 */
public class GitHubRepository extends Repository {

    public static final Logger LOG = Logger.getLogger(GitHubRepository.class.getCanonicalName());

    private static final List<Token> tokens = new ArrayList<>();
    private int testID = 0;

    private final GitWrapper git;
    private final Repository repo;
    private final Gson gson;
    private final HttpClient hc;

    private final String apiBaseURL;
    private final File dir;

    private final AtomicBoolean allowGuessing = new AtomicBoolean(false);

    /**
     * Create a wrapper around a (local) Repository with additional information about GitHub hosted repositories.
     *
     * @param repo       the local repository
     * @param git        the GitWrapper instance to use
     * @param oauthToken a list of valid oAuth token for GitHub
     *                   (see  <a href="https://github.com/settings/tokens">https://github.com/settings/tokens</a>) for
     *                   information about creating such tokens)
     */
    public GitHubRepository(Repository repo, GitWrapper git, List<String> oauthToken) {
        this.repo = repo;
        String repoUrl = repo.getUrl();
        if (repoUrl.contains("git@")) {
            repoUrl = repoUrl.replace(":", "/").replace("git@", "https://");
        }
        apiBaseURL = repoUrl.replace(".git", "").replace("//github.com/", "//api.github.com/repos/");
        LOG.fine(String.format("Creating repo for %s", apiBaseURL));
        this.git = git;
        dir = repo.getDir();

        synchronized (tokens) {
            for (String token : oauthToken) {
                tokens.add(new Token(token));
            }
        }

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
     * Gets a List of Issues.
     *
     * @return optionally a List of IssueData or an empty Optional if an error occurred
     */
    public JsonArray getIssues(String cachedir) {
        JsonParser parser = new JsonParser();
        List<Integer> cachedIds = new ArrayList<>();
        PrintWriter cacheWriter = null;
        try {
            FileWriter fw = new FileWriter(cachedir + "/cache.json", true);
            BufferedWriter bw = new BufferedWriter(fw);
            cacheWriter = new PrintWriter(bw);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] cache;

        String cacheText = "";
        try {
            cache = Files.readAllBytes(Paths.get(cachedir + "/cache.json"));
            cacheText = new String(cache, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] cachedIssues = cacheText.split("\n");
        String cacheArrayString = Arrays.toString(cachedIssues);
        JsonArray cachedArray = (JsonArray) parser.parse(cacheArrayString);
        for(JsonElement iss : cachedArray) {
            JsonObject issobj = iss.getAsJsonObject();
            cachedIds.add(issobj.get("number").getAsInt());
        }
        Optional<String> test = getJSONStringFromPath("/issues?state=all");

        JsonElement jsonData;
        if (test.isPresent())
            jsonData = parser.parse(test.get());
        else
            return null;
        JsonArray array = jsonData.getAsJsonArray();
        List<JsonElement> input = new ArrayList<>();
        for (JsonElement jsonElement : array) {
            input.add(jsonElement);
        }

        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(tokens.size() - 1));
        LOG.info("Starting to deserialize issues.");
        PrintWriter finalCacheWriter = cacheWriter;
        input.parallelStream().forEach(json -> {
            //This gives the working threads an id from '0' to 'number of thread - 1' which is later used for accessing the tokens.
            //Not a very nice solution but the only one that I found.
            setThreadId();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if(!cachedIds.contains(json.getAsJsonObject().get("number").getAsInt())) {
                IssueData issue;
                try {
                    issue = gson.fromJson(json, new TypeToken<IssueData>() {
                    }.getType());
                } catch (JsonSyntaxException e) {
                    LOG.warning("Encountered invalid JSON: " + json);
                    issue = null;
                }
                LOG.info("Thread " + Thread.currentThread().getName() + "(" + Thread.currentThread().getId() + ") deserrialized issue number " + (issue != null ? issue.number : 0));
                synchronized (finalCacheWriter) {
                    finalCacheWriter.println(serialize(issue));
                }
            } else
                LOG.info("Issue " + json.getAsJsonObject().get("number").getAsInt() +  " is loaded from  cache.");
        });
        finalCacheWriter.close();

        LOG.info("Finished issue deserialization.");

        byte[] encoded;
        String text = "empty";
        try {
            encoded = Files.readAllBytes(Paths.get(cachedir + "/cache.json"));
            text = new String(encoded, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] items = text.split("\n");
        String jsonArrayString = Arrays.toString(items);

        return (JsonArray) parser.parse(jsonArrayString);
    }

    private synchronized void setThreadId() {
        if(testID < tokens.size()) {
            Thread.currentThread().setName(String.valueOf(testID));
            testID++;
        }
    }

    /**
     * Returns a List of Events for an Issue.
     *
     * @param issue the parent IssueData
     * @return optionally a List of EventData or an empty Optional if an error occurred
     */
    Optional<List<EventData>> getEvents(IssueData issue) {
        return getJSONStringFromPath("/issues/" + issue.number + "/events").map(json -> {
            try {
                return gson.fromJson(json, new TypeToken<ArrayList<EventData>>() {
                }.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
        });
    }

    /**
     * Returns a List of Comments for an Issue.
     *
     * @param issue the parent IssueData
     * @return optionally a list of CommentData or an empty Optional if an error occurred
     */
    Optional<List<CommentData>> getComments(IssueData issue) {
        return getJSONStringFromPath("/issues/" + issue.number + "/comments?state=all").map(json -> {
            try {
                return gson.fromJson(json, new TypeToken<ArrayList<CommentData>>() {
                }.getType());
            } catch (JsonSyntaxException e) {
                LOG.warning("Encountered invalid JSON: " + json);
                return null;
            }
        });
    }

    /**
     * Gets a List of all Commits before a given Date.
     *
     * @param date   the Date until Commits are included
     * @param branch limit Commits to this specific branch
     * @return optionally a List of Commits or an empty Optional if the operation failed
     */
    public Optional<List<Commit>> getCommitsBeforeDate(Date date, String branch) {
        return getCommitsInRange(null, date, branch, false);
    }

    /**
     * Gets a List of all Commits before a given Date on a branch.
     *
     * @param start      the Date since which Commits are included
     * @param end        the Date until Commits are included
     * @param branch     limit Commits to this specific branch
     * @param onlyMerges if {@code true} only merge Commits are included
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
     * @param path the API path to call
     * @return an InputStreamReader on the result
     */
    private Optional<String> getJSONStringFromPath(String path) {
        return getJSONStringFromURL(apiBaseURL + path);
    }

    /**
     * Returns an InputStreamReader reading the JSON data returned from the GitHub API called with the given URL.
     * The caller is responsible, that the URL matches this repository.
     *
     * @param urlString the URL to call
     * @return an InputStreamReader on the result
     */
    Optional<String> getJSONStringFromURL(String urlString) {
        String json;
        LOG.fine(String.format("Getting json from %s", urlString));
        Token token;
        if (Thread.activeCount() > 1) {
            token = tokens.get(Integer.valueOf(Thread.currentThread().getName()));
        } else
            token = tokens.get(0);

        if(!token.isUsable()) {
            LOG.info("Token has run out of attemts, " + "Thread " + Thread.currentThread() + " waiting 60 mins.");
            try {
                Thread.sleep(3600000);
            } catch (InterruptedException e) {
                LOG.warning("Thread " + Thread.currentThread() + " was unexpectedly woken up.");
            }
            LOG.info("Thread " + Thread.currentThread() + " is continuing work after sleep");
        }
        try {
            List<String> data = new ArrayList<>();
            String url = urlString + (urlString.contains("?") ? "&" : "?") + "per_page=100";
            try {
                do {
                    HttpResponse resp = hc.execute(new HttpGet(url + (token.getToken().isEmpty() ? "" : "&access_token=" + token.getToken())));
                    if (resp.getStatusLine().getStatusCode() != 200) {
                        LOG.warning(String.format("Could not access api method: %s returned %s", url, resp.getStatusLine()));
                        resp.getEntity().getContent().close();
                        return Optional.empty();
                    }

                    Map<String, List<String>> headers = Arrays.stream(resp.getAllHeaders())
                            .collect(Collectors.toMap(Header::getName,
                                    h -> new ArrayList<>(Collections.singletonList(h.getValue())),
                                    (a, b) -> {
                                        a.addAll(b);
                                        return a;
                                    }));
                    int rateLimitRemaining = Integer.parseInt(headers.getOrDefault("X-RateLimit-Remaining", Collections.singletonList("")).get(0));
                    Instant rateLimitReset = Instant.ofEpochMilli(Long.parseLong(headers.get("X-RateLimit-Reset").get(0)) * 1000);
                    token.update(rateLimitRemaining, rateLimitReset);

                    // if the call failed, fetch a new token and try again.
                    if (!token.isUsable()) {
                        LOG.info("Token has run out of attemts, " + "Thread " + Thread.currentThread() + " waiting 60 mins.");
                        Thread.sleep(3600000);
                        LOG.info("Thread " + Thread.currentThread() + " is continuing work after sleep");
                    }
                    Optional<String> next = Arrays.stream(headers.getOrDefault("Link", new ArrayList<>(Collections.singleton(""))).get(0).split(","))
                            .filter(link -> link.contains("next")).findFirst();
                    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(resp.getEntity().getContent()))) {
                        data.add(buffer.lines().collect(Collectors.joining("\n")));
                    }
                    resp.getEntity().getContent().close();

                    if (!next.isPresent()) break;
                    String nextUrl = next.get();
                    url = nextUrl.substring(nextUrl.indexOf("<") + 1, nextUrl.indexOf(">"));
                } while (true);

            } catch (InterruptedException e) {
                LOG.warning("Thread " + Thread.currentThread() + " was unexpectedly woken up.");
            }
            // concatenate all results together, making one large JSON string
            json = String.join("", data).replace("][", ",");

        } catch (IOException e) {
            LOG.warning("Could not get data from GitHub.");
            return Optional.empty();
        }

        return json == null || json.isEmpty() ? Optional.empty() : Optional.of(json);
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
     * @param guess if {@code true}, guessing of user email is allowed
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
     * @param obj the object to serialize
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
