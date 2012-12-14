package org.yamcs.xtce;

import java.io.Serializable;

public class ValueEnumerationRange implements Serializable {
	private static final long serialVersionUID = 2011023231432L;
	
    double min = 0;
    double max = 0;
    boolean isMinInclusive = true;
    boolean isMaxInclusive = true;
    String label;
    
    public ValueEnumerationRange(double min, double max, boolean isMinInclusive, boolean isMaxInclusive, String label) {
        assert(min < max);
        this.min = min;
        this.max = max;
        this.isMaxInclusive = isMaxInclusive;
        this.isMinInclusive = isMinInclusive;
        this.label = label;
    }
    
    public boolean isValueInRange(long value) {
        return ( (isMinInclusive) ? (value >= min) : (value > min) ) && ( (isMaxInclusive) ? (value <= max) : (value < max) );
    }
    
    public String getLabel() {
        return label;
    }
}
