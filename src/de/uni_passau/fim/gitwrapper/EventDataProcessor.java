/**
 * Copyright (C) 2016-2018 Florian Heck
 * Copyright (C) 2019 Thomas Bock
 * Copyright (C) 2025 Leo Sendelbach
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
import io.gsonfire.PostProcessor;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * The EventDataProcessor helps with mapping events to their specific subclasses and filling fields that cannot be
 * filled directly from the JSON
 */
class EventDataProcessor implements JsonDeserializer<EventData>, JsonSerializer<EventData> {

    private static final Logger LOG = Logger.getLogger(EventDataProcessor.class.getCanonicalName());

    private static Map<String, Class> map = new TreeMap<>();

    static {
        map.put("", EventData.DefaultEventData.class);
        map.put("labeled", EventData.LabeledEventData.class);
        map.put("unlabeled", EventData.LabeledEventData.class);
        map.put("referenced", EventData.ReferencedEventData.class);
        map.put("merged", EventData.ReferencedEventData.class);
        map.put("closed", EventData.ReferencedEventData.class);
        map.put("review_requested", EventData.RequestedReviewEventData.class);
        map.put("review_request_removed", EventData.RequestedReviewEventData.class);
        map.put("review_dismissed", EventData.DismissedReviewEventData.class);
        map.put("assigned", EventData.AssignedEventData.class);
        map.put("unassigned", EventData.AssignedEventData.class);
    }

    @Override
    public EventData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return context.deserialize(json, map.getOrDefault(json.getAsJsonObject().get("event").getAsString(), map.get("")));
    }

    @Override
    public JsonElement serialize(EventData src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(src, src.getClass());
    }

    /**
     * Processor for events that reference commits.
     */
    static class ReferencedEventProcessor implements PostProcessor<EventData.ReferencedEventData> {

        private GitHubRepository repo;

        /**
         * Creates a new EventDataProcessor for the given repo.
         *
         * @param repo
         *         the repo
         */
        ReferencedEventProcessor(GitHubRepository repo) {
            this.repo = repo;
        }

        @Override
        public void postDeserialize(EventData.ReferencedEventData result, JsonElement src, Gson gson) {
            JsonElement hash = src.getAsJsonObject().get("commit_id");
            if (hash.isJsonNull()) {
                return;
            }

            result.commit = repo.getGithubCommit(hash.getAsString()).orElseGet(() -> {
                LOG.warning("Found commit unknown to GitHub and local git repo: " + hash + " Retry using url...");
                JsonElement url = src.getAsJsonObject().get("commit_url");
                return repo.getGithubCommitUrl(hash.getAsString(), url.getAsString()).orElseGet(() -> {
                    LOG.warning("Could not find commit: " + hash);
                    return null;
                });
            });
        }

        @Override
        public void postSerialize(JsonElement result, EventData.ReferencedEventData src, Gson gson) { }
    }

    /**
     * Processor for events that manipulate labels.
     */
    static class LabeledEventProcessor implements PostProcessor<EventData.LabeledEventData> {

        @Override
        public void postDeserialize(EventData.LabeledEventData result, JsonElement src, Gson gson) {
            result.added = src.getAsJsonObject().get("event").getAsString().equals("labeled");
        }

        @Override
        public void postSerialize(JsonElement result, EventData.LabeledEventData src, Gson gson) { }
    }

    /**
     * Processor for events that request a reviewer.
     */
    static class RequestedReviewEventProcessor implements PostProcessor<EventData.RequestedReviewEventData> {

        @Override
        public void postDeserialize(EventData.RequestedReviewEventData result, JsonElement src, Gson gson) {
        }

        @Override
        public void postSerialize(JsonElement result, EventData.RequestedReviewEventData src, Gson gson) { }
    }


    /**
     * Processor for events that dismiss a review.
     */
    static class DismissedReviewEventProcessor implements PostProcessor<EventData.DismissedReviewEventData> {

        @Override
        public void postDeserialize(EventData.DismissedReviewEventData result, JsonElement src, Gson gson) {
            JsonObject dismissedReview = src.getAsJsonObject().get("dismissed_review").getAsJsonObject();
            result.reviewId = dismissedReview.get("review_id").getAsInt();
            result.state = dismissedReview.get("state").getAsString();
            if (!dismissedReview.get("dismissal_message").isJsonNull()) {
               result.dismissalMessage = dismissedReview.get("dismissal_message").getAsString();
            }
            if (dismissedReview.get("dismissal_commit_id") != null
                && !dismissedReview.get("dismissal_commit_id").isJsonNull()) {
               result.dismissalCommitId = dismissedReview.get("dismissal_commit_id").getAsString();
           }
        }

        @Override
        public void postSerialize(JsonElement result, EventData.DismissedReviewEventData src, Gson gson) { }
    }

    /**
     * Processor for assign events.
     */
    static class AssignedEventProcessor implements PostProcessor<EventData.AssignedEventData> {

        @Override
        public void postDeserialize(EventData.AssignedEventData result, JsonElement src, Gson gson) {
        }

        @Override
        public void postSerialize(JsonElement result, EventData.AssignedEventData src, Gson gson) { }
    }
}
