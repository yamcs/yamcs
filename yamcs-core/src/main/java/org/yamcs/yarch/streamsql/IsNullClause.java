package org.yamcs.yarch.streamsql;

public class IsNullClause {
    // if negation = true, the clause is "IS NOT NULL"
    boolean negation;

    public void setNegation(boolean b) {
        this.negation = b;        
    }
}
