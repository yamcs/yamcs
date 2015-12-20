package org.yamcs.xtce;

import java.io.Serializable;

/**
 * An integral range of numbers.  "min", and "max"
 * @author nm
 *
 */
public class IntegerRange implements Serializable {
    private static final long serialVersionUID = 1L;

    long minInclusive;
    long maxInclusive;
    public IntegerRange(long minInclusive, long maxInclusive) {
        this.minInclusive=minInclusive;
        this.maxInclusive=maxInclusive;
    }

    public long getMaxInclusive() {
        return maxInclusive;
    }
    public long getMinInclusive() {
        return minInclusive;
    }

    /**
     * E.g. a low limit of ]-Infinity, -22] and a high limit of [40, +Infinity[ 
     * intersect to [-22, 40] (which for practical purposes is actually the range
     * inside of which pvals are _not_ out of limits)
     */
    public IntegerRange intersectWith(IntegerRange other) {
        return new IntegerRange(Math.max(minInclusive, other.minInclusive), Math.min(maxInclusive, other.maxInclusive));
    }

    @Override
    public String toString() {
        return "["+minInclusive+","+maxInclusive+"]";
    }
    
    public String toString(boolean signed) {
        if(signed) {
            return "["+minInclusive+","+maxInclusive+"]";
        } else {
            return "["+Long.toUnsignedString(minInclusive)+","+Long.toUnsignedString(maxInclusive)+"]";
        }
    }

}
