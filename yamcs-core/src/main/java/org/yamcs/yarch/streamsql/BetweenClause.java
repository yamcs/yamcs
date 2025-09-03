package org.yamcs.yarch.streamsql;

public class BetweenClause {
    boolean negation = false;
    Expression expr1, expr2;
    
    public BetweenClause(Expression expr1, Expression expr2, boolean negation) {
        this.expr1 = expr1;
        this.expr2 = expr2;
        this.negation = negation;
    }
}
