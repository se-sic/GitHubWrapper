/**
 * Copyright (C) 2016-2018 Florian Heck
 *
 * This file is part of GitHubWrapper.
 *
 * GitHubWrapper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GitHubWrapper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GitWrapper. If not, see <http://www.gnu.org/licenses/>.
 */
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
