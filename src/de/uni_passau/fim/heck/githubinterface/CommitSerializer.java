package de.uni_passau.fim.heck.githubinterface;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import de.uni_passau.fim.seibt.gitwrapper.repo.Commit;

/**
 * The CommitSerializer allows for serialization of Commits to JSON using the GSON library.
 */
public class CommitSerializer implements JsonSerializer<Commit> {

    @Override
    public JsonElement serialize(Commit src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("author", src.getAuthor());
        obj.addProperty("time", src.getAuthorTime().toString());
        obj.addProperty("hash", src.getId());
        return obj;
    }
}
