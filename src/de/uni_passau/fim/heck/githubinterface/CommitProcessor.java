package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.*;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The CommitProcessor allows for serialization and deserialization of Commits to JSON using the GSON library.
 */
public class CommitProcessor implements JsonSerializer<Commit>, JsonDeserializer<Commit> {

    private static final Logger LOG = Logger.getLogger(CommitProcessor.class.getCanonicalName());

    private final GitHubRepository repo;

    /**
     * Creates a new CommitProcessor for serializing and deserializing {@link Commit Commits}.
     *
     * @param repo
     *         the repo containing the Commits
     */
    CommitProcessor(GitHubRepository repo) {
        this.repo = repo;
    }

    @Override
    public JsonElement serialize(Commit src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        String maybeTime = Optional.ofNullable(src.getAuthorTime()).map(OffsetDateTime::toString).orElse(null);
        obj.addProperty("author", src.getAuthor());
        obj.addProperty("time", maybeTime);
        obj.addProperty("hash", src.getId());
        return obj;
    }

    @Override
    public Commit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonElement commit;
        if ((commit = json.getAsJsonObject().get("sha")) != null) {
            String hash = commit.getAsString();
            Optional<Commit> match = repo.getCommitByHashOrMessage(hash, json.getAsJsonObject().get("commit").getAsJsonObject().get("message").getAsString());
            return match.orElse(repo.getCommitUnchecked(hash));
        } else if ((commit = json.getAsJsonObject().get("hash")) != null) {
            return repo.getCommit(commit.getAsString()).orElse(repo.getCommitUnchecked(""));
        } else {
            LOG.warning("Could not find commit hash");
            return repo.getCommitUnchecked("");
        }
    }
}
