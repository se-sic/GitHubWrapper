package de.uni_passau.fim.heck.githubinterface;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

public class Token {

    private ReentrantLock lock = new ReentrantLock();

    private final String token;
    private int calls;
    private Instant resetTime;

    Token(String token) {
        this.token = token;
        this.calls = Integer.MAX_VALUE;
        this.resetTime = Instant.now();
    }

    Token(String token, int calls, Instant resetTime) {
        this.token = token;
        this.calls = calls;
        this.resetTime = resetTime;
    }

    public String getToken() {
        return token;
    }

    void update(int calls, Instant resetTime) {
        this.calls = calls;
        this.resetTime = resetTime;
    }

    boolean isValid() {
        return calls > 0 || Instant.now().isAfter(resetTime);
    }

    public boolean isUsable() {
        return (!lock.isLocked() || lock.isHeldByCurrentThread()) && isValid();
    }

    boolean acquire() {
        return lock.tryLock();
    }

    void release() {
        lock.unlock();
    }

    Instant getResetTime() {
        return resetTime;
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
