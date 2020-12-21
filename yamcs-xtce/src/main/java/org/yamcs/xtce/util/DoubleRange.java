package org.yamcs.xtce.util;

import java.io.Serializable;

/**
 * A range of numbers [min, max) where both min and max can be inclusive or exclusive.
 * <p>
 * Both min and max can be Double.NaN meaning that the range is open at that end.
 * 
 * @author nm
 *
 */
public class DoubleRange implements Serializable {

    private static final long serialVersionUID = 3L;

    final double min;
    final double max;
    final boolean minIncl;
    final boolean maxIncl;

    public DoubleRange(double min, double max, boolean minIncl, boolean maxIncl) {
        this.min = min;
        this.max = max;
        this.minIncl = minIncl;
        this.maxIncl = maxIncl;
    }

    public DoubleRange(double minInclusive, double maxInclusive) {
        this.min = minInclusive;
        this.max = maxInclusive;
        this.minIncl = true;
        this.maxIncl = true;
    }

    //copy constructor
    public DoubleRange(DoubleRange range) {
        this.min = range.min;
        this.max = range.max;
        this.minIncl = range.minIncl;
        this.maxIncl = range.maxIncl;
    }

    /**
     * Returns a range from the XTCE float range used for alarms which is in fact a union of two ranges
     * 
     * @param minExclusive
     * @param maxExclusive
     * @param minInclusive
     * @param maxInclusive
     * @return
     */
    public static DoubleRange fromXtceComplement(double minExclusive, double maxExclusive, double minInclusive,
            double maxInclusive) {
        double min = minExclusive;
        double max = maxExclusive;
        boolean minIncl = false;
        boolean maxIncl = false;

        if (!Double.isNaN(minInclusive)) {
            min = minInclusive;
            minIncl = true;
        }
        if (!Double.isNaN(maxInclusive)) {
            max = maxInclusive;
            maxIncl = true;
        }

        return new DoubleRange(min, max, minIncl, maxIncl);
    }

    public double getMax() {
        return max;
    }

    public double getMin() {
        return min;
    }

    public boolean isMinInclusive() {
        return minIncl;
    }

    public boolean isMaxInclusive() {
        return maxIncl;
    }

    /**
     * Checks if the value is in range.
     * 
     * @param v
     * @return &lt;0 =0 or &gt;0 if the value v is lower than min, between min and max or greater than max respectively.
     */
    public int inRange(double v) {
        if (!Double.isNaN(min) && ((minIncl && v < min) || (!minIncl && v <= min))) {
            return -1;
        }

        if (!Double.isNaN(max) && ((maxIncl && v > max) || (!maxIncl && v >= max))) {
            return 1;
        }

        return 0;
    }

    /**
     * E.g. a low limit of ]-Infinity, -22] and a high limit of [40, +Infinity[ intersect to [-22, 40] (which for
     * practical purposes is actually the range inside of which pvals are _not_ out of limits)
     */
    public DoubleRange intersectWith(DoubleRange other) {
        double xmin = Double.NEGATIVE_INFINITY;
        boolean xminExcl = true;
        if (!Double.isNaN(min) && min > xmin) {
            xmin = min;
            xminExcl = minIncl;
        }
        if (!Double.isNaN(other.min) && ((other.minIncl && other.min > xmin) || other.min >= xmin)) {
            xmin = other.min;
            xminExcl = other.minIncl;
        }

        if (Double.isInfinite(xmin)) {
            xmin = Double.NaN;
        }

        double xmax = Double.POSITIVE_INFINITY;
        boolean xmaxExcl = true;
        if (!Double.isNaN(max) && max < xmax) {
            xmax = max;
            xmaxExcl = maxIncl;
        }
        if (!Double.isNaN(other.max) && ((other.maxIncl && other.max < xmax) || other.max <= xmax)) {
            xmax = other.max;
            xmaxExcl = other.maxIncl;
        }

        if (Double.isInfinite(xmax)) {
            xmax = Double.NaN;
        }

        return new DoubleRange(xmin, xmax, xminExcl, xmaxExcl);
    }

    @Override
    public String toString() {
        return (minIncl ? "[" : "(") + (Double.isNaN(min) ? "-inf" : min) + "," + (Double.isNaN(max) ? "+inf" : max)
                + (maxIncl ? "]" : ")");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(max);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (maxIncl ? 1231 : 1237);
        temp = Double.doubleToLongBits(min);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (minIncl ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DoubleRange other = (DoubleRange) obj;
        if (Double.doubleToLongBits(max) != Double.doubleToLongBits(other.max))
            return false;
        if (Double.doubleToLongBits(min) != Double.doubleToLongBits(other.min))
            return false;
        if (minIncl != other.minIncl)
            return false;
        if (maxIncl != other.maxIncl)
            return false;
        return true;
    }
}
