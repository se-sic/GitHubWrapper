package de.uni_passau.fim.heck.githubinterface.token;

import java.util.ArrayList;
import java.util.List;

public class TokenPool {
    private static final TokenPool INSTANCE = new TokenPool();
    private List<Token> tokens = new ArrayList<>();
    
    private TokenPool() {
    }
    
    public static TokenPool getInstance() {
        return INSTANCE;
    }
    
    public int getNumberOfTokens(){
        return tokens.size();
    }
    
    public void addToken(Token token) {
        boolean alreadyThere = false;
        
        if(tokens == null){
            tokens.add(token);
            alreadyThere = true;
        }
        
        for (Token oldToken : tokens){
            if (oldToken.toString().equals(token.getToken())){
                alreadyThere = true;
                break;
            }
        }
        
        if (!alreadyThere){
            tokens.add(token);
        }
    }
    
    public void addTokens(List<Token> tokens) {
        for (Token token : tokens) { 
            addToken(token);
        }
    }
    
    public synchronized Token getToken() {
        while (true) {
            for (Token token : tokens) {
                if (token.isValid()) {
                    return token;
                }
            }
            
            //Make it sleep for five minutes
            try {
                Thread.sleep(5 * 60 * 1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.err.println("All tokens exceeded their rate-limit. Wait more");
        }
    }
}
