package org.yamcs.ui.packetviewer.filter.ast;

import java.util.List;

public class OrExpression implements Node {

    private List<AndExpression> clauses;

    public OrExpression(List<AndExpression> clauses) {
        this.clauses = clauses;
    }

    public List<AndExpression> getClauses() {
        return clauses;
    }

    @Override
    public String toString(String indent) {
        StringBuilder buf = new StringBuilder(indent)
                .append(getClass().getSimpleName())
                .append("\n");
        for (AndExpression clause : clauses) {
            buf.append(clause.toString(indent + " |"));
        }
        return buf.toString();
    }
}
