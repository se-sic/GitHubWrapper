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
 * Data representation of a review.
 */
public class ReviewData {

    @SerializedName(value = "user", alternate = {"actor"})
    UserData user;
    OffsetDateTime submitted_at;
    String state;
    boolean hasReviewInitialComment;

    private List<ReferencedLink<ReviewCommentData>> reviewComments;

    @SerializedName(value = "reviewId", alternate = {"id"})
    int reviewId; // necessary for joining reviews and their commits, will be removed by the corresponding processor

    public ReviewData() {
        this.hasReviewInitialComment = false;
    }

    /**
     * The User that created the Review.
     */
    public UserData getUser() {
        return user;
    }

    /**
     * The date and time the Review was created.
     */
    public OffsetDateTime getSubmissionDate() {
        return submitted_at;
    }

    /**
     * The review state.
     */
    public String getState() {
        return state;
    }

    /**
     * Whether the review contains an initial review comment.
     */
    public boolean hasReviewInitialComment() {
        return hasReviewInitialComment;
    }

    /**
     * This list of comments belonging to a review.
     */
    public List<ReferencedLink<ReviewCommentData>> getReviewComments() {
        return reviewComments;
    }

    /**
     * Sets a list of comments to this review.
     *
     * @param reviewComments
     *         the list of related review comments
     */
    void setReviewComments(List<ReferencedLink<ReviewCommentData>> reviewComments) {
        this.reviewComments = reviewComments;
    }

    /**
     * The id of the review.
     */
    public int getReviewId() {
        return reviewId;
    }



    /**
     * A generic review.
     */
    class DefaultReviewData extends ReviewData {}

    /**
    * A sceleton object to deserialize a {@link ReviewInitialCommentDataComment} object
    */
    class ReviewInitialCommentData extends ReviewData {

        String body;

        /**
         * For use by the deserializer.
         */
        ReviewInitialCommentData() {
            this.hasReviewInitialComment = true;
        }

        /**
         * The comment of the review.
         */
        public String getBody() {
            return body;
        }
    }
}
