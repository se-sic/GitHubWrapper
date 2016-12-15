package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class EventDataDeserializer implements JsonDeserializer<EventData> {

    private static Map<String, Class> map = new TreeMap<>();

    static {
        map.put("", EventData.DefaultEventData.class);
        map.put("labeled", EventData.LabeledEventData.class);
        map.put("referenced", EventData.LabeledEventData.class);
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
}