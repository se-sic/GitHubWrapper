package de.uni_passau.fim.gitwrapper;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;

/**
 * (De-)Serializer for OffsetDateTime. Delegates to {@link OffsetDateTime#parse(CharSequence)} and
 * {@link OffsetDateTime#toString()} respectively.
 */
public class OffsetDateTimerProcessor implements JsonDeserializer<OffsetDateTime>, JsonSerializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return OffsetDateTime.parse(json.getAsString());
    }

    @Override
    public JsonElement serialize(OffsetDateTime src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }
}