package org.yamcs.utils.parser.ast;

public class UnaryExpression implements Node {

    private Comparison comparison;
    private AndExpression andExpression;
    private boolean not;

    public UnaryExpression(Comparison comparison, boolean not) {
        this.comparison = comparison;
        this.not = not;
    }

    public UnaryExpression(AndExpression andExpression, boolean not) {
        this.andExpression = andExpression;
        this.not = not;
    }

    public Comparison getComparison() {
        return comparison;
    }

    public boolean isNot() {
        return not;
    }

    public AndExpression getAndExpression() {
        return andExpression;
    }

    @Override
    public String toString(String indent) {
        StringBuilder buf = new StringBuilder(indent)
                .append(getClass().getSimpleName())
                .append("\n");
        if (not) {
            buf.append(indent).append(" |").append("NOT\n");
        }
        if (comparison != null) {
            buf.append(comparison.toString(indent + " |")).append("\n");
        } else {
            buf.append(andExpression.toString(indent + " |"));
        }
        return buf.toString();
    }
}
