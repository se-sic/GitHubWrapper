package de.uni_passau.fim.heck.githubinterface;

import java.util.Date;

public class Token {

    private final String token;
    private int calls;
    private Date resetTime;

    Token(String token) {
        this.token = token;
        this.calls = Integer.MAX_VALUE;
        this.resetTime = new Date();
    }

    Token(String token, int calls, Date resetTime) {
        this.token = token;
        this.calls = calls;
        this.resetTime = resetTime;
    }

    public String getToken() {
        return token;
    }

    void update(int calls, Date resetTime) {
        this.calls = calls;
        this.resetTime = resetTime;
    }

    public boolean isValid() {
       return calls > 0 || new Date().after(resetTime);
    }

    Date getResetTime() {
        return resetTime;
    }

    @Override
    public String toString() {
        return token;
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
