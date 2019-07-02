package de.uni_passau.fim.gitwrapper;

import com.google.gson.annotations.SerializedName;

import java.time.OffsetDateTime;

/**
 * Data representation of a review.
 */
public class ReviewData {

    @SerializedName(value = "user", alternate = {"actor"})
    UserData user;
    OffsetDateTime submitted_at;
    String state;
    boolean hasReviewInitialComment;

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
