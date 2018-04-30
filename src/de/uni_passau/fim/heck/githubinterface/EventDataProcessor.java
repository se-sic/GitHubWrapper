package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import io.gsonfire.PostProcessor;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * The EventDataProcessor helps with mapping events to their specific subclasses and filling fields that cannot be
 * filled directly from the JSON
 */
class EventDataProcessor implements JsonDeserializer<EventData>, JsonSerializer<EventData>, PostProcessor<EventData.ReferencedEventData> {
    private static final Logger LOG = Logger.getLogger(EventDataProcessor.class.getCanonicalName());

    private static Map<String, Class> map = new TreeMap<>();
    private final GitHubRepository repo;

    /**
     * Creates a new EventDataProcessor for the given repo.
     *
     * @param repo
     *         the repo
     */
    EventDataProcessor(GitHubRepository repo) {
        this.repo = repo;
    }

    static {
        map.put("", EventData.DefaultEventData.class);
        map.put("labeled", EventData.LabeledEventData.class);
        map.put("referenced", EventData.ReferencedEventData.class);
        map.put("merged", EventData.ReferencedEventData.class);
        map.put("closed", EventData.DefaultEventData.class);
    }

    @Override
    public EventData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        String type = json.getAsJsonObject().get("event").getAsString();
        Class c = map.get(type);
        if (c == null) {
            c = map.get("");
        }
        return context.deserialize(json, c);
    }

    @Override
    public JsonElement serialize(EventData src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(src, src.getClass());
    }

    @Override
    public void postDeserialize(EventData.ReferencedEventData result, JsonElement src, Gson gson) {
        String hash = src.getAsJsonObject().get("commit_id").getAsString();
        String commitInfo = repo.getJSONStringFromURL(src.getAsJsonObject().get("commit_url").getAsString()).get();
        String message = ((JsonElement) gson.fromJson(commitInfo, new TypeToken<JsonElement>() {}.getType()))
                .getAsJsonObject().get("commit").getAsJsonObject().get("message").getAsString();
        result.commit = repo.getCommitByHashOrMessage(hash, message).orElse(repo.getCommitUnchecked(hash));
    }

    @Override
    public void postSerialize(JsonElement result, EventData.ReferencedEventData src, Gson gson) { }
}
