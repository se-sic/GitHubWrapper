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
                LOG.warning("Found commit unknown to GitHub and local git repo: " + hash);
                return null;
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

}
