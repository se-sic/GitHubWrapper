package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.*;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;

import java.lang.reflect.Type;
import java.util.logging.Logger;

/**
 * The CommitSerializer allows for serialization of Commits to JSON using the GSON library.
 */
public class CommitSerializer implements JsonSerializer<Commit>, JsonDeserializer<Commit> {

    private static final Logger LOG = Logger.getLogger(CommitSerializer.class.getCanonicalName());

    private final Repository repo;

    CommitSerializer(Repository repo) {
        this.repo = repo;
    }

    @Override
    public JsonElement serialize(Commit src, Type typeOfSrc, JsonSerializationContext context) {
        // make sure lazy initialization has kicked in
        src.getAuthor();

        JsonObject obj = new JsonObject();
        obj.addProperty("author", src.getAuthor());
        obj.addProperty("time", src.getAuthorTime().toString());
        obj.addProperty("hash", src.getId());
        return obj;
    }

    @Override
    public Commit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonElement commit;
        String hash;
        if ((commit = json.getAsJsonObject().get("sha")) != null) {
            hash = commit.getAsString();
        } else if ((commit = json.getAsJsonObject().get("hash")) != null) {
            hash = commit.getAsString();
        } else {
            LOG.warning("Could not find commit hash");
            hash = "";
        }

        return repo.getCommit(hash).orElseGet(() -> {
            LOG.warning("Could not find commit " + json.toString() + "in repo " + repo.getName());
            return null;
        });

    }
}
