package org.yamcs.ui.packetviewer.filter.ast;

import java.util.List;

public class AndExpression implements Node {

    private List<UnaryExpression> clauses;

    public AndExpression(List<UnaryExpression> clauses) {
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
