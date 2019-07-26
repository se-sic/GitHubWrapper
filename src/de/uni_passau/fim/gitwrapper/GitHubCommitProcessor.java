package de.uni_passau.fim.gitwrapper;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.logging.Logger;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.gitwrapper.UserData.CommitUserData;

/**
 * The GitHubCommitProcessor allows for serialization and deserialization of GitHubCommits to JSON using the GSON library.
 */
public class GitHubCommitProcessor implements JsonSerializer<GitHubCommit>, JsonDeserializer<GitHubCommit> {

    private static final Logger LOG = Logger.getLogger(CommitProcessor.class.getCanonicalName());

    private final GitHubRepository repo;

    /**
     * Creates a new GitHubCommitProcessor for serializing and deserializing {@link GitHubCommits}.
     *
     * @param repo
     *         the repo containing the Commits
     */
    GitHubCommitProcessor(GitHubRepository repo, UserDataProcessor userStore) {
        this.repo = repo;
    }

    @Override
    public JsonElement serialize(GitHubCommit src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        String maybeTime = Optional.ofNullable(src.getAuthorTime()).map(OffsetDateTime::toString).orElse(null);

        UserData author = new UserData();
        author.name = src.getAuthor();
        author.email = src.getAuthorMail();
        author.username = src.getAuthorUsername();

        UserData committer = new UserData();
        committer.name = src.getCommitter();
        committer.email = src.getCommitterMail();
        committer.username = src.getCommitterUsername();

        obj.add("author", context.serialize(author));
        obj.add("committer", context.serialize(committer));
        obj.addProperty("message", src.getMessage());
        obj.addProperty("time", maybeTime);
        obj.addProperty("hash", src.getId());
        // just for reference, add marker if this commit can be found in the local git repository
        obj.addProperty("is_in_git", src.getCommitter() != null);

        return obj;
    }

    @Override
    public GitHubCommit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonElement commit;
        if ((commit = json.getAsJsonObject().get("sha")) != null) {
            // reading directly from github
            JsonObject commitData = json.getAsJsonObject().get("commit").getAsJsonObject();
            CommitUserData author = context.deserialize(commitData.get("author"), new TypeToken<CommitUserData>() {}.getType());
            CommitUserData committer = context.deserialize(commitData.get("committer"), new TypeToken<CommitUserData>() {}.getType());

            if (!json.getAsJsonObject().get("author").isJsonNull()) {
                try {
                    author.githubUsername = json.getAsJsonObject().get("author").getAsJsonObject().get("login").getAsString();
                } catch (NullPointerException e) {
                    LOG.info("Author username not available for commit " + commit.getAsString());
                }
            }
            if (!json.getAsJsonObject().get("committer").isJsonNull()) {
                try {
                    committer.githubUsername = json.getAsJsonObject().get("committer").getAsJsonObject().get("login").getAsString();
                } catch (NullPointerException e) {
                    LOG.info("Committer username not available for commit " + commit.getAsString());
                }
            }
            return repo.getReferencedCommit(commit.getAsString(), commitData.get("message").getAsString(), author, committer);

        } else if ((commit = json.getAsJsonObject().get("hash")) != null) {
            // reading from dump

            /*UserData user = context.deserialize(json.getAsJsonObject().get("author"), new TypeToken<UserData>() {}.getType());
            CommitUserData commitUser = new CommitUserData(user.name, user.email, user.username,
                    context.deserialize(json.getAsJsonObject().get("time"), new TypeToken<OffsetDateTime>() {}.getType()));
            return repo.getGHCommit(commit.getAsString()).orElse(repo.getReferencedCommit(commit.getAsString(),
                    json.getAsJsonObject().get("message").getAsString(), commitUser));*/

            GitHubCommit ghc = new GitHubCommit(this.repo, commit.getAsString());
            ghc.setMessage(json.getAsJsonObject().get("message").getAsString());

            OffsetDateTime time = context.deserialize(json.getAsJsonObject().get("time"), new TypeToken<OffsetDateTime>() {}.getType());
            ghc.setAuthorTime(time);

            ghc.setAuthor(json.getAsJsonObject().get("author").getAsJsonObject().get("name").getAsString());
            ghc.setAuthorMail(json.getAsJsonObject().get("author").getAsJsonObject().get("email").getAsString());
            ghc.setCommitter(json.getAsJsonObject().get("committer").getAsJsonObject().get("name").getAsString());
            ghc.setCommitterMail(json.getAsJsonObject().get("committer").getAsJsonObject().get("email").getAsString());

            if (!json.getAsJsonObject().get("author").getAsJsonObject().get("username").isJsonNull()) {
                ghc.setAuthorUsername(json.getAsJsonObject().get("author").getAsJsonObject().get("username").getAsString());
            }
            if (!json.getAsJsonObject().get("committer").getAsJsonObject().get("username").isJsonNull()) {
                ghc.setCommitterUsername(json.getAsJsonObject().get("committer").getAsJsonObject().get("username").getAsString());
            }
            return ghc;

        } else {
            LOG.severe("Could not find a commit hash.");
            return repo.getGHCommitUnchecked(DummyCommit.DUMMY_COMMIT_ID);
        }
    }
}
