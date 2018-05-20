package de.uni_passau.fim.gitwrapper;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.gsonfire.PostProcessor;

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
        return context.deserialize(json, map.getOrDefault(json.getAsJsonObject().get("event").getAsString(), map.get("")));
    }

    @Override
    public JsonElement serialize(EventData src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(src, src.getClass());
    }

    @Override
    public void postDeserialize(EventData.ReferencedEventData result, JsonElement src, Gson gson) {
        String hash = src.getAsJsonObject().get("commit_id").getAsString();
        result.commit = repo.getGithubCommit(hash).orElseGet(() -> {
            LOG.warning("Found commit unknown to GitHub and local git repo: " + hash);
            return null;
        });
    }

    @Override
    public void postSerialize(JsonElement result, EventData.ReferencedEventData src, Gson gson) { }
}
