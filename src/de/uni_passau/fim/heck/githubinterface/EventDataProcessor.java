package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.*;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;
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
    private final Repository repo;

    /**
     * Creates a new EventDataProcessor for the given repo.
     *
     * @param repo
     *         the repo
     */
    EventDataProcessor(Repository repo) {
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
        result.commit = repo.getCommit(hash).orElseGet(() -> {
            LOG.warning("Could not get commit for hash " + hash + " in repo " + repo.getName());
            return null;
        });
    }

    @Override
    public void postSerialize(JsonElement result, EventData.ReferencedEventData src, Gson gson) { }
}
