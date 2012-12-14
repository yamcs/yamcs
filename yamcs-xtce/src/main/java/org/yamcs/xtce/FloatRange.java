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
	
	double minInclusive;
	double maxInclusive;
	public FloatRange(double minInclusive, double maxInclusive) {
		this.minInclusive=minInclusive;
		this.maxInclusive=maxInclusive;
	}
	
	public double getMaxInclusive() {
		return maxInclusive;
	}
	public double getMinInclusive() {
		return minInclusive;
	}
	
	@Override
    public String toString() {
        return "["+minInclusive+","+maxInclusive+"]";
    }
}
