package org.yamcs.yarch.streamsql;

public class LikeClause {
    boolean negation;
    String pattern; 
    public void setNegation(boolean b) {
        this.negation = b;        
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
        
    }
}
