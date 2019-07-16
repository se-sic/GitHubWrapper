package de.uni_passau.fim.gitwrapper;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.time.OffsetDateTime;

/**
 * A skeleton for information about Events.
 */
public abstract class EventData {

    @SerializedName(value = "user", alternate = {"actor"})
    UserData user;
    OffsetDateTime created_at;
    String event;

    /**
     * The User that created the Event.
     */
    public UserData getUser() {
        return user;
    }

    /**
     * The date and time the Event was created.
     */
    public OffsetDateTime getCreateDate() {
        return created_at;
    }

    /**
     * The event typ.
     */
    public String getEvent() {
        return event;
    }

    /**
     * A generic event.
     */
    class DefaultEventData extends EventData {}

    /**
     * An Event generated by a change of labels.
     */
    public class LabeledEventData extends EventData {

        LabelData label;
        boolean added = true;

        /**
         * The name of the label.
         */
        public String getName() {
            return label.name;
        }

        /**
         * The color of the label as hex code.
         */
        public String getColor() {
            return label.color;
        }

        /**
         * A short description of the label.
         */
        public String getDescription() {
            return label.description;
        }

        /**
         * Checks it the label was added with this event.
         *
         * @return {@code true} if this LabelEvent has added a label
         */
        public boolean isAdded() {
            return added;
        }

        /**
         * Information about the label
         */
        class LabelData {
            String name;
            String color;
            String description;
        }
    }

    /**
     * An Event generated by referencing an {@link IssueData#number Issue} in a {@link Commit}.
     */
    public class ReferencedEventData extends EventData {

        @Expose(deserialize = false)
        Commit commit;

        /**
         * The commit references.
         */
        public Commit getCommit() {
            return commit;
        }
    }

    /**
     * An Event generated by requesting a review from someone.
     */
    public class RequestedReviewEventData extends EventData {

        @SerializedName(value = "requestedReviewer", alternate = {"requested_reviewer"})
        UserData requestedReviewer;

        /**
         * The requested reviewer.
         */
        public UserData getRequestedReviewer() {
            return requestedReviewer;
        }
    }

    /**
     * An Event generated by dismissing a review.
     */
    public class DismissedReviewEventData extends EventData {

        int reviewId;
        String state;
        String dismissalMessage;
        String dismissalCommitId;

        /**
         * The id of the dismissed review.
         */
        public int getReviewId() {
            return reviewId;
        }

        /**
         * The state of the dismissed review.
         */
        public String getState() {
            return state;
        }

        /**
         * The dismissal message of the review dismision.
         */
        public String getDismissalMessage() {
            return dismissalMessage;
        }

        /**
         * The dismissal commit of the review dismission.
         */
        public String getDismissalCommitId() {
            return dismissalCommitId;
        }
    }

    /**
     * An Event generated by assigning an issue to somebody.
     */
    public class AssignedEventData extends EventData {

        UserData assigner;

        /**
         * The assigner.
         */
        public UserData getAssigner() {
            return assigner;
        }
    }
}
