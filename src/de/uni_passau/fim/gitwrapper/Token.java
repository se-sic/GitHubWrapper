package de.uni_passau.fim.gitwrapper;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents an API token for use with the GitHub API.
 */
public class Token {

    private final ReentrantLock lock = new ReentrantLock();

    private final String token;
    private int calls;
    private Instant resetTime;

    /**
     * Creates a new Token with unlimited calls and a reset time in the past.
     *
     * @param token
     *         the API token.
     */
    Token(String token) {
        this.token = token;
        this.calls = Integer.MAX_VALUE;
        this.resetTime = Instant.now();
    }

    /**
     * Creates a new Token.
     *
     * @param token
     *         the API token
     * @param calls
     *         the number of reaming calls
     * @param resetTime
     *         the Instant, when the limit is reset
     */
    Token(String token, int calls, Instant resetTime) {
        this.token = token;
        this.calls = calls;
        this.resetTime = resetTime;
    }

    /**
     * Gets the API token.
     *
     * @return the token
     */
    Optional<String> getToken() {
        return lock.isHeldByCurrentThread() ? Optional.of(token) : Optional.empty();
    }

    /**
     * Gets the reset time.
     *
     * @return the Instant, the call limit is reset by GitHub
     */
    Instant getResetTime() {
        return resetTime;
    }

    /**
     * Updates the usage of the Token.
     *
     * @param calls
     *         the new number of remaining calls
     * @param resetTime
     *         the new reset time
     */
    void update(int calls, Instant resetTime) {
        this.calls = calls;
        this.resetTime = resetTime;
    }

    /**
     * Checks if this token is valid for one single API call.
     *
     * @return {@code true}, if one call can be made, or the token can be reset
     */
    boolean isValid() {
        return calls > 1 || Instant.now().isAfter(resetTime);
    }

    /**
     * Checks if this token is usable by the current Thread and is valid.
     *
     * @return {@code true}, if the token is valid and the lock is not locked or locked by the current Thread.
     * @see #acquire()
     * @see #isValid()
     */
    boolean isUsable() {
        return (!lock.isLocked() || lock.isHeldByCurrentThread()) && isValid();
    }

    /**
     * Checks if the token is held by the caller.
     *
     * @return {@code true}, iff the the calling thread is already holding the token
     */
    boolean isHeld() {
        return lock.isHeldByCurrentThread();
    }

    /**
     * Tries to acquires the lock on this Token, so it can be used.
     *
     * @return {@code true}, it the lock was acquired.
     * @see #release()
     */
    boolean acquire() {
        return lock.tryLock();
    }

    /**
     * Releases the lock.
     *
     * @see #acquire()
     */
    void release() {
        lock.unlock();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;

        Token token1 = (Token) o;

        return token != null ? token.equals(token1.token) : token1.token == null;
    }

    @Override
    public int hashCode() {
        return token != null ? token.hashCode() : 0;
    }
}
