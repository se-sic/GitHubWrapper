/**
 * Copyright (C) 2016-2018 Florian Heck
 * Copyright (C) 2019 Thomas Bock
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

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.logging.Logger;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import de.uni_passau.fim.gitwrapper.UserData.CommitUserData;

/**
 * The CommitProcessor allows for serialization and deserialization of Commits to JSON using the GSON library.
 */
public class CommitProcessor implements JsonSerializer<Commit>, JsonDeserializer<Commit> {

    private static final Logger LOG = Logger.getLogger(CommitProcessor.class.getCanonicalName());

    private final GitHubRepository repo;
    private final UserDataProcessor userStore;

    /**
     * Creates a new CommitProcessor for serializing and deserializing {@link Commit Commits}.
     *
     * @param repo
     *         the repo containing the Commits
     * @param userStore
     *         the corresponding user store
     */
    CommitProcessor(GitHubRepository repo, UserDataProcessor userStore) {
        this.repo = repo;
        this.userStore = userStore;
    }

    @Override
    public JsonElement serialize(Commit src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        String maybeTime = Optional.ofNullable(src.getAuthorTime()).map(OffsetDateTime::toString).orElse(null);
        obj.add("author", context.serialize(userStore.getUserByName(src.getAuthor()).orElseGet(() -> {
            UserData user = new UserData();
            user.name = src.getAuthor();
            user.email = src.getAuthorMail();
            return user;
        })));
        obj.addProperty("message", src.getMessage());
        obj.addProperty("time", maybeTime);
        obj.addProperty("hash", src.getId());
        // just for reference, add marker if this commit can be found in the local git repository
        obj.addProperty("is_in_git", src.getCommitter() != null);
        return obj;
    }

    @Override
    public Commit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonElement commit;
        if ((commit = json.getAsJsonObject().get("sha")) != null) {
            // reading directly from github
            JsonObject commitData = json.getAsJsonObject().get("commit").getAsJsonObject();
            CommitUserData author = context.deserialize(commitData.get("author"), new TypeToken<CommitUserData>() {}.getType());
            return repo.getReferencedCommit(commit.getAsString(), commitData.get("message").getAsString(), author);

        } else if ((commit = json.getAsJsonObject().get("hash")) != null) {
            // reading from dump
            UserData user = context.deserialize(json.getAsJsonObject().get("author"), new TypeToken<UserData>() {}.getType());
            CommitUserData commitUser = new CommitUserData(user.name, user.email,
                    context.deserialize(json.getAsJsonObject().get("time"), new TypeToken<OffsetDateTime>() {}.getType()));
            return repo.getCommit(commit.getAsString()).orElse(repo.getReferencedCommit(commit.getAsString(),
                    json.getAsJsonObject().get("message").getAsString(), commitUser));

        } else {
            LOG.severe("Could not find a commit hash.");
            return repo.getCommitUnchecked(DummyCommit.DUMMY_COMMIT_ID);
        }
    }
}
