/**
 * Copyright (C) 2018 Florian Heck
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
            if(json.getAsJsonObject().get("path") != null) {
                // review comment
                result = new ReferencedLink<ReviewCommentData>();
                ReviewCommentData commentData = new ReviewCommentData();
                commentData.file =  json.getAsJsonObject().get("path").getAsString();
                if (!json.getAsJsonObject().get("position").isJsonNull()) {
                    commentData.position = json.getAsJsonObject().get("position").getAsInt();
                } else {
                    commentData.position = null;
                }
                if (!json.getAsJsonObject().get("original_position").isJsonNull()) {
                    commentData.original_position = json.getAsJsonObject().get("original_position").getAsInt();
                } else {
                    commentData.original_position = null;
                }
                if (!json.getAsJsonObject().get("body").isJsonNull()) {
                    commentData.body = json.getAsJsonObject().get("body").getAsString();
                } else {
                    commentData.body = "";
                }
                commentData.commit_id = json.getAsJsonObject().get("commit_id").getAsString();
                commentData.original_commit_id = json.getAsJsonObject().get("original_commit_id").getAsString();
                result.target = commentData;
            } else {
                // normal comment
                result = new ReferencedLink<String>();
                if (!json.getAsJsonObject().get("body").isJsonNull()) {
                    result.target = json.getAsJsonObject().get("body").getAsString();
                } else {
                    result.target = "";
                }
            }
        } else switch (json.getAsJsonObject().get("type").getAsString()) {
            case "comment":
                if(json.getAsJsonObject().get("file") != null) {
                    // review comment
                    result = new ReferencedLink<ReviewCommentData>();
                    ReviewCommentData commentData = new ReviewCommentData();
                    commentData.file =  json.getAsJsonObject().get("file").getAsString();
                    if (!json.getAsJsonObject().get("position").isJsonNull()) {
                        commentData.position = json.getAsJsonObject().get("position").getAsInt();
                    } else {
                        commentData.position = null;
                    }
                    if (!json.getAsJsonObject().get("original_position").isJsonNull()) {
                       commentData.original_position = json.getAsJsonObject().get("original_position").getAsInt();
                    } else {
                       commentData.original_position = null;
                    }
                    commentData.commit_id = json.getAsJsonObject().get("commit_id").getAsString();
                    commentData.original_commit_id = json.getAsJsonObject().get("original_commit_id").getAsString();
                    commentData.body = json.getAsJsonObject().get("body").getAsString();
                    result.target = commentData;
                } else {
                    // normal comment
                    result = new ReferencedLink<String>();
                    result.target = json.getAsJsonObject().get("body").getAsString();
                }
                break;
            case "issue":
            case "pullrequest":
                result = new ReferencedLink<Integer>();
                result.target = json.getAsJsonObject().get("number").getAsInt();
                break;
            case "commit":
            case "commitAddedToPullRequest":
            case "commitMentionedInIssue":
            case "commitReferencesIssue":
                result = new ReferencedLink<GitHubCommit>();
                result.target = context.deserialize(json.getAsJsonObject().get("commit"), new TypeToken<GitHubCommit>() {}.getType());

                boolean addedToPullRequest = json.getAsJsonObject().get("type").getAsString().equals("commitAddedToPullRequest");
                ((GitHubCommit) result.target).setAddedToPullRequest(addedToPullRequest);
                result.type = json.getAsJsonObject().get("type").getAsString();
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
                result.getAsJsonObject().remove("target");
                break;
            case "ReviewCommentData":
                result.getAsJsonObject().addProperty("type", "comment");
                result.getAsJsonObject().addProperty("body", ((ReviewCommentData) src.getTarget()).getBody());
                result.getAsJsonObject().addProperty("file", ((ReviewCommentData) src.getTarget()).getFile());
                result.getAsJsonObject().addProperty("position", ((ReviewCommentData) src.getTarget()).getPosition());
                result.getAsJsonObject().addProperty("original_position", ((ReviewCommentData) src.getTarget()).getOriginalPosition());
                result.getAsJsonObject().addProperty("commit_id", ((ReviewCommentData) src.getTarget()).getCommitId());
                result.getAsJsonObject().addProperty("original_commit_id", ((ReviewCommentData) src.getTarget()).getOriginalCommitId());
                result.getAsJsonObject().remove("target");
                break;
            case "Integer":
            case "PullRequestData":
            case "IssueData":
                result.getAsJsonObject().addProperty("type", "issue");
                result.getAsJsonObject().addProperty("number", ((Integer) src.getTarget()));
                result.getAsJsonObject().remove("target");
                break;
            case "Commit":
            case "GitHubCommit":
                if (src.getType() == null) {
                    result.getAsJsonObject().addProperty("type", "commit");
                }
                result.getAsJsonObject().add("commit", result.getAsJsonObject().get("target"));
                result.getAsJsonObject().remove("target");
                break;
            default:
                result.getAsJsonObject().addProperty("type", "unknown");
                LOG.warning("Encountered unknown reference type: " + src.target.getClass().getSimpleName());
                break;
        }
    }
}

