package org.yamcs.utils.parser.ast;

import java.util.regex.Pattern;

public class Comparison implements Node {

    public final String comparable;
    public final Comparator comparator;
    public final String value;

    public final Pattern pattern;
    public final byte[] binary;

    public Comparison(String comparable, Comparator comparator, String value, Pattern pattern, byte[] binary) {
        this.comparable = comparable;
        this.comparator = comparator;
        this.value = value;
        this.pattern = pattern;
        this.binary = binary;
    }

    @Override
    public String toString(String indent) {
        return indent + getClass().getSimpleName() + "\n" +
                indent + " |" + comparable + "\n" +
                indent + " |" + comparator + "\n" +
                indent + " |" + value;
    }
}
