/**
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

import com.google.gson.annotations.SerializedName;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Data representation of a review comment and its position.
 */
public class ReviewCommentData {

    Integer position;
    Integer original_position;
    String commit_id;
    String original_commit_id;
    @SerializedName(value = "file", alternate = {"path"})
    String file;
    String body;
    boolean suggestion = false;

    public ReviewCommentData() {
    }

    /**
     * The position of the review comment in the file (at its latest state).
     */
    public Integer getPosition() {
        return position;
    }

    /**
     * The original postion of the review comment in the file.
     */
    public Integer getOriginalPosition() {
        return original_position;
    }

    /**
     * The id of the commit which the position belongs to.
     */
    public String getCommitId() {
        return commit_id;
    }

   /**
     * The id of the commit at which this review comment was initially added.
     */
    public String getOriginalCommitId() {
        return original_commit_id;
    }

    /**
     * The file which is commented by this review comment.
     */
    public String getFile() {
        return file;
    }

    /**
     * The comment itself.
     */
    public String getBody() {
        return body;
    }

    /**
     * If the comment contains a suggestion
     */
    public boolean containsSuggestion() {
        return suggestion;
    }
}
