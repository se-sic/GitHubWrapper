package de.uni_passau.fim.heck.githubinterface.token;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

public class Token {

    private String token;
    private int calls = 5000;
    
    public Token(String token) {
        this.token = token;
    }
    
    public Token(String token, int calls, long resetTime) {
        this.token = token;
    }
    
    public String getToken() {
        return token;
    }
    
    public int getCalls() {
        return calls;
    }
    
    public void updateTokenCalls(int calls) {
        this.calls = calls;
    }
    
    public boolean isValid() {
        HttpClient hc = HttpClients.createDefault();
        HttpResponse resp;
        int calls = 0;
        try {
            resp = hc.execute(new HttpGet("https://api.github.com/rate_limit?access_token=" + getToken()));
            
            Map<String, List<String>> headers = Arrays.stream(resp.getAllHeaders()).collect(Collectors
            .toMap(Header::getName, h -> new ArrayList<>(Collections.singletonList(h.getValue())), (a, b) -> {
            a.addAll(b); return a;}));
            calls = Integer.valueOf(headers.getOrDefault("X-RateLimit-Remaining", Arrays.asList("")).get(0));
            resp.getEntity().getContent().close();
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        // 1 just in case
        return calls > 1;
    }
    
    public String toString() 
        return getToken();
    }
}
