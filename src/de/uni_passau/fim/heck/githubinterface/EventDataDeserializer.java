package de.uni_passau.fim.heck.githubinterface;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.EventData;

/**
 * The EventDataDeserializer helps with mapping events to their specific subclasses.
 */
public class EventDataDeserializer implements JsonDeserializer<EventData>, JsonSerializer<EventData> {

    private static Map<String, Class> map = new TreeMap<>();

    static {
        map.put("", EventData.DefaultEventData.class);
        map.put("closed", EventData.DefaultEventData.class);
        map.put("labeled", EventData.DefaultEventData.class);
        map.put("referenced", EventData.DefaultEventData.class);
        map.put("merged", EventData.DefaultEventData.class);
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
}
