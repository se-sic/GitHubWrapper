package de.uni_passau.fim.gitwrapper;

import com.google.gson.annotations.SerializedName;

import java.time.OffsetDateTime;

/**
 * Wrapper for additional information about referenced elements.
 */
public class ReferencedLink<T> {

    T target;
    UserData user;
    @SerializedName(value = "referenced_at", alternate = {"created_at"})
    OffsetDateTime referenced_at;
    String type;

    /**
     * Creates an empty ReferenceLink.
     */
    ReferencedLink() { }

    /**
     * Creates a new ReferencedIssueData wrapper for adding additional information to linked elements.
     *
     * @param target
     *         the referenced element
     * @param user
     *         the referencing user
     * @param referencedTime
     *         the referencing time
     */
    ReferencedLink(T target, UserData user, OffsetDateTime referencedTime) {
        this.target = target;
        this.user = user;
        this.referenced_at = referencedTime;
    }

    /**
     * Creates a new ReferencedIssueData wrapper for adding additional information to linked elements.
     *
     * @param target
     *         the referenced element
     * @param user
     *         the referencing user
     * @param referencedTime
     *         the referencing time
     * @param type
     *         the type of the reference
     */
    ReferencedLink(T target, UserData user, OffsetDateTime referencedTime, String type) {
        this.target = target;
        this.user = user;
        this.referenced_at = referencedTime;
        this.type = type;
    }

    /**
     * Gets the target element.
     *
     * @return the link target
     */
    public T getTarget() {
        return target;
    }

    /**
     * Gets the user that created the reference.
     *
     * @return the linking user
     */
    public UserData getUser() {
        return user;
    }

    /**
     * Gets the time, the reference was created.
     *
     * @return the link time
     */
    public OffsetDateTime getLinkTime() {
        return referenced_at;
    }

    /**
     * Gets the type of the reference
     *
     * @return the type of the reference
     */
    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReferencedLink<?> that = (ReferencedLink<?>) o;

        if (target != null ? !target.equals(that.target) : that.target != null) return false;
        if (user != null ? !user.equals(that.user) : that.user != null) return false;
        return referenced_at != null ? referenced_at.equals(that.referenced_at) : that.referenced_at == null;
    }

    @Override
    public int hashCode() {
        int result = target != null ? target.hashCode() : 0;
        result = 31 * result + (user != null ? user.hashCode() : 0);
        result = 31 * result + (referenced_at != null ? referenced_at.hashCode() : 0);
        return result;
    }
}
