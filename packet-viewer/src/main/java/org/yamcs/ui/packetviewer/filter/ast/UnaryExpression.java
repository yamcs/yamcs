package org.yamcs.ui.packetviewer.filter.ast;

public class UnaryExpression implements Node {

    private Comparison comparison;
    private OrExpression orExpression;
    private boolean not;

    public UnaryExpression(Comparison comparison, boolean not) {
        this.comparison = comparison;
        this.not = not;
    }

    public UnaryExpression(OrExpression orExpression, boolean not) {
        this.orExpression = orExpression;
        this.not = not;
    }

    public Comparison getComparison() {
        return comparison;
    }

    public boolean isNot() {
        return not;
    }

    public OrExpression getOrExpression() {
        return orExpression;
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
            buf.append(orExpression.toString(indent + " |"));
        }
        return buf.toString();
    }
}
