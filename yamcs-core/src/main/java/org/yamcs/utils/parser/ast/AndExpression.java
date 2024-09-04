package org.yamcs.utils.parser.ast;

import java.util.List;

public class AndExpression implements Node {

    private List<OrExpression> clauses;

    public AndExpression(List<OrExpression> clauses) {
        this.clauses = clauses;
    }

    public List<OrExpression> getClauses() {
        return clauses;
    }

    @Override
    public String toString(String indent) {
        StringBuilder buf = new StringBuilder(indent)
                .append(getClass().getSimpleName())
                .append("\n");
        for (OrExpression clause : clauses) {
            buf.append(clause.toString(indent + " |"));
        }
        return buf.toString();
    }
}
