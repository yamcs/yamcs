package org.yamcs.utils.parser.ast;

import java.util.List;

public class OrExpression implements Node {

    private List<UnaryExpression> clauses;

    public OrExpression(List<UnaryExpression> clauses) {
        this.clauses = clauses;
    }

    public List<UnaryExpression> getClauses() {
        return clauses;
    }

    @Override
    public String toString(String indent) {
        StringBuilder buf = new StringBuilder(indent)
                .append(getClass().getSimpleName())
                .append("\n");
        for (UnaryExpression clause : clauses) {
            buf.append(clause.toString(indent + " |"));
        }
        return buf.toString();
    }
}
