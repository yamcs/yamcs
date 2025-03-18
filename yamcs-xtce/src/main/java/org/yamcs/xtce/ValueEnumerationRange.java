package org.yamcs.xtce;

import java.io.Serializable;

public class ValueEnumerationRange implements Serializable {
    private static final long serialVersionUID = 2011023231432L;

    double min = 0;
    double max = 0;
    boolean isMinInclusive = true;
    boolean isMaxInclusive = true;
    String label;
    private String description;

    public ValueEnumerationRange(double min, double max, boolean isMinInclusive, boolean isMaxInclusive, String label) {
        assert (min < max);
        this.min = min;
        this.max = max;
        this.isMaxInclusive = isMaxInclusive;
        this.isMinInclusive = isMinInclusive;
        this.label = label;
    }

    public boolean isValueInRange(long value) {
        return ((isMinInclusive) ? (value >= min) : (value > min))
                && ((isMaxInclusive) ? (value <= max) : (value < max));
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public boolean getMinInclusive() {
        return isMinInclusive;
    }

    public boolean getMaxInclusive() {
        return isMaxInclusive;
    }
}
