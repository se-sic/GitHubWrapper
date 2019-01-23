package de.uni_passau.fim.gitwrapper;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.gsonfire.PostProcessor;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.logging.Logger;

public class ReferencedLinkProcessor implements JsonDeserializer<ReferencedLink>, PostProcessor<ReferencedLink> {

    private static final Logger LOG = Logger.getLogger(ReferencedLink.class.getCanonicalName());

    private final GitHubRepository repo;

    /**
     * Creates a new ReferencedLinkProcessor for handling links from Issues specific to the provided GitHubRepository
     * to other elements on this repo.
     * @param repo the GitHubRepository
     */
    ReferencedLinkProcessor(GitHubRepository repo) {
        this.repo = repo;
    }

    @Override
    public ReferencedLink deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        ReferencedLink result = new ReferencedLink<>();
        if (json.getAsJsonObject().get("type") == null) {
            // comment from github
            result = new ReferencedLink<String>();
            if (!json.getAsJsonObject().get("body").isJsonNull()) {
                result.target = json.getAsJsonObject().get("body").getAsString();
            } else {
                result.target = "";
            }
        } else switch (json.getAsJsonObject().get("type").getAsString()) {
            case "comment":
                result = new ReferencedLink<String>();
                result.target = json.getAsJsonObject().get("body").getAsString();
                break;
            case "issue":
            case "pullrequest":
                result = new ReferencedLink<Integer>();
                result.target = json.getAsJsonObject().get("number").getAsInt();
                break;
            case "commit":
                result = new ReferencedLink<Commit>();
                result.target = repo.getGithubCommit(json.getAsJsonObject().get("commit_id").getAsString()).orElse(null);
                break;
            default:
                LOG.warning("Encountered unknown reference type!");
                break;
        }

        result.user = context.deserialize(json.getAsJsonObject().get("user"), new TypeToken<UserData>() {}.getType());
        JsonElement timeElement = json.getAsJsonObject().get("referenced_at");
        if (timeElement == null || timeElement.isJsonNull()) {
            timeElement = json.getAsJsonObject().get("created_at");
        }
        if (!(timeElement == null || timeElement.isJsonNull())) {
            result.referenced_at = context.deserialize(timeElement, new TypeToken<OffsetDateTime>() {}.getType());
        }

        return result;
    }

    @Override
    public void postDeserialize(ReferencedLink result, JsonElement src, Gson gson) { }

    @Override
    public void postSerialize(JsonElement result, ReferencedLink src, Gson gson) {
        switch (src.target.getClass().getSimpleName()) {
            case "String":
                result.getAsJsonObject().addProperty("type", "comment");
                result.getAsJsonObject().addProperty("body", ((String) src.getTarget()));
                break;
            case "Integer":
            case "PullRequestData":
            case "IssueData":
                result.getAsJsonObject().addProperty("type", "issue");
                result.getAsJsonObject().addProperty("number", ((Integer) src.getTarget()));
                break;
            case "Commit":
                result.getAsJsonObject().addProperty("type", "commit");
                result.getAsJsonObject().addProperty("commit_id", ((Commit) src.getTarget()).id);
                break;
            default:
                result.getAsJsonObject().addProperty("type", "unknown");
                LOG.warning("Encountered unknown reference type: " + src.target.getClass().getSimpleName());
                break;
        }
    }
}

