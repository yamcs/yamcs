package org.yamcs.xtce;

import java.io.Serializable;

/**
 * A range of numbers.  "minInclusive", "minExclusive", "maxInclusive" and "maxExclusive" attributes are 
 * borrowed from the W3C schema language.
 * @author nm
 *
 */
public class FloatRange implements Serializable {
    private static final long serialVersionUID = 200706052351L;

    double minExclusive;
    double maxExclusive;
    public FloatRange(double minExclusive, double maxExclusive) {
        this.minExclusive = minExclusive;
        this.maxExclusive = maxExclusive;
    }

    public double getMaxExclusive() {
        return maxExclusive;
    }
    public double getMinExclusive() {
        return minExclusive;
    }

    /**
     * E.g. a low limit of ]-Infinity, -22] and a high limit of [40, +Infinity[ 
     * intersect to [-22, 40] (which for practical purposes is actually the range
     * inside of which pvals are _not_ out of limits)
     */
    public FloatRange intersectWith(FloatRange other) {
        return new FloatRange(Math.max(minExclusive, other.minExclusive), Math.min(maxExclusive, other.maxExclusive));
    }

    @Override
    public String toString() {
        return "["+minExclusive+","+maxExclusive+"]";
    }
}
