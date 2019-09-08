package de.uni_passau.fim.gitwrapper;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.gsonfire.PostProcessor;

import java.lang.reflect.Type;
import java.util.logging.Logger;

/**
 * The ReviewDataProcessor helps with mapping reviews to their specific subclasses and filling fields that cannot be
 * filled directly from the JSON
 */
class ReviewDataProcessor implements JsonDeserializer<ReviewData>, JsonSerializer<ReviewData> {

    private static final Logger LOG = Logger.getLogger(ReviewDataProcessor.class.getCanonicalName());

    @Override
    public ReviewData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonElement body = json.getAsJsonObject().get("body");
        if(body != null && !body.isJsonNull() && body.getAsString().equals("")) {
            return context.deserialize(json, ReviewData.DefaultReviewData.class);
        } else {
            return context.deserialize(json, new TypeToken<ReviewData.ReviewInitialCommentData>() {}.getType());
        }
    }

    @Override
    public JsonElement serialize(ReviewData src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(src, src.getClass());
    }

    /**
     * Processor for reviews that contain an initial comment.
     */
    static class ReviewInitialCommentDataProcessor implements PostProcessor<ReviewData.ReviewInitialCommentData> {

        private GitHubRepository repo;

        /**
         * Creates a new ReviewInitialCommentDataProcessor for the given repo.
         *
         * @param repo
         *         the repo
         */
        ReviewInitialCommentDataProcessor(GitHubRepository repo) {
            this.repo = repo;
        }

        @Override
        public void postDeserialize(ReviewData.ReviewInitialCommentData result, JsonElement src, Gson gson) {
            JsonElement body = src.getAsJsonObject().get("body");
            if (body.isJsonNull()) {
                return;
            }

            result.body = body.getAsString();
            result.hasReviewInitialComment = true;
        }

        @Override
        public void postSerialize(JsonElement result, ReviewData.ReviewInitialCommentData src, Gson gson) { }
    }
}
