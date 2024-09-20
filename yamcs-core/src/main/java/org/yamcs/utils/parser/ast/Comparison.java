package org.yamcs.utils.parser.ast;

import java.util.regex.Pattern;

public class Comparison implements Node {

    /**
     * Either a field name (in the case of a field comparison), or plain text (in the case of text search).
     * <p>
     * Always in lowercase.
     */
    public final String comparable;
    public final Comparator comparator;

    /**
     * Value to compare against.
     * <p>
     * Always in lowercase, except when a regular expression match is done.
     */
    public final String value;

    public final Pattern pattern;
    public final byte[] binary;

    public Comparison(String comparable, Comparator comparator, String value, Pattern pattern, byte[] binary) {
        this.comparable = comparable.toLowerCase();
        this.comparator = comparator;
        this.pattern = pattern;
        this.binary = binary;

        if (value != null && pattern == null) {
            this.value = value.toLowerCase();
        } else { // Preserve case when pattern matching
            this.value = value;
        }
    }

    @Override
    public String toString(String indent) {
        return indent + getClass().getSimpleName() + "\n" +
                indent + " |" + comparable + "\n" +
                indent + " |" + comparator + "\n" +
                indent + " |" + value;
    }
}
