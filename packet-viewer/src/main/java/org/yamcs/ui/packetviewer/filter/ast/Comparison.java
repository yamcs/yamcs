package org.yamcs.ui.packetviewer.filter.ast;

public class Comparison implements Node {

    public final String ref;
    public final Operator op;
    public final String comparand;

    public Comparison(String ref, Operator op, String comparand) {
        this.ref = ref;
        this.op = op;
        this.comparand = comparand;
    }

    @Override
    public String toString(String indent) {
        return indent + getClass().getSimpleName() + "\n" +
                indent + " |" + ref + "\n" +
                indent + " |" + op + "\n" +
                indent + " |" + comparand;
    }
}
