package de.uni_passau.fim.heck.githubinterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

public class Token {

	private static final Logger LOG = Logger.getLogger(GitHubRepository.class.getCanonicalName());
	
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

    synchronized void update(int calls, Date resetTime) {
        this.calls = calls;
        this.resetTime = resetTime;
    }

    public boolean isValid() {
       return calls > 10;
    }
    
    public boolean isUpdatedValid() {
        startingToken();
    	return calls > 10;
     }

    Date getResetTime() {
        return resetTime;
    }
    
    public synchronized boolean startingToken() {

		final HttpClient hc = HttpClients.createDefault();

		HttpResponse resp;
		try {
			resp = hc.execute(new HttpGet("https://api.github.com/rate_limit?access_token=" + token));

			Map<String, List<String>> headers = Arrays.stream(resp.getAllHeaders()).collect(Collectors
					.toMap(Header::getName, h -> new ArrayList<>(Collections.singletonList(h.getValue())), (a, b) -> {
						a.addAll(b);
						return a;
					}));

			int rateLimitRemaining = Integer
					.parseInt(headers.getOrDefault("X-RateLimit-Remaining", Collections.singletonList("")).get(0));
			Date rateLimitReset = new Date(Long.parseLong(headers.get("X-RateLimit-Reset").get(0)) * 1000);
			update(rateLimitRemaining, rateLimitReset);
			
			resp.getEntity().getContent().close();
		} catch (IOException e) {
			LOG.warning("Could not get data from GitHub.");
		}
		return true;
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
