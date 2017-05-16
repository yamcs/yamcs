package org.yamcs.xtce;

import java.io.Serializable;

/**
 * A range of numbers [min, max) where both min and max can be inclusive or exclusive.
 *  
 * @author nm
 *
 */
public class FloatRange implements Serializable {
    private static final long serialVersionUID = 3L;

    double min = Double.NaN;
    double max = Double.NaN;
    boolean minIncl = true;
    boolean maxIncl = true;
   
    public FloatRange(double min, double max, boolean minIncl, boolean maxIncl) {
        this.min = min;
        this.max = max;
        this.minIncl = minIncl;
        this.maxIncl = maxIncl;
    }
   
    public FloatRange(double minInclusive, double maxInclusive) {
        this.min = minInclusive;
        this.max = maxInclusive;
    }
    
    /**
     * Returns a range from the XTCE float range used for alarms which is in fact a union of two ranges
     * @param minExclusive
     * @param maxExclusive
     * @param minInclusive
     * @param maxInclusive
     * @return
     */
    public static FloatRange fromXtceComplement(double minExclusive, double maxExclusive, double minInclusive, double maxInclusive) {
        double min = minExclusive;
        double max = maxExclusive;
        boolean minIncl = true;
        boolean maxIncl = true;
        
        if(!Double.isNaN(minInclusive)) {
            min = minInclusive;
            minIncl = false;
        }
        if(!Double.isNaN(maxInclusive)) {
            max = maxInclusive;
            maxIncl = false;
        }
        
        return new FloatRange(min, max, minIncl, maxIncl);
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
     * returns <0 =0 or >0 if the value v is lower than min, between min and max or greater than max respectively.
     * @param v
     * @return
     */
    public int inRange(double v) {
        if(!Double.isNaN(min) && ((minIncl && v<min) || v<=min)) { 
                return -1;
        }
        System.out.println("max: "+max+" maxIncl: "+maxIncl+" v: "+v+" v>max: "+(v>max));
        if(!Double.isNaN(max) && ((maxIncl && v>max) || v>=max)) {
            System.out.println("return 1");
            return 1;
        }
        System.out.println("return 0");
        
        return 0;
    }

    /**
     * E.g. a low limit of ]-Infinity, -22] and a high limit of [40, +Infinity[ 
     * intersect to [-22, 40] (which for practical purposes is actually the range
     * inside of which pvals are _not_ out of limits)
     */
    public FloatRange intersectWith(FloatRange other) {
        double xmin = Double.NEGATIVE_INFINITY;
        boolean xminExcl = true;
        if(!Double.isNaN(min) && min>xmin) {
            xmin = min;
            xminExcl = minIncl;
        }
        if(!Double.isNaN(other.min) && ((other.minIncl&&other.min>xmin) || other.min>=xmin)) {
            xmin = other.min;
            xminExcl = other.minIncl;
        }
        
        if(Double.isInfinite(xmin)) {
           xmin = Double.NaN;
        }
        
        double xmax = Double.POSITIVE_INFINITY;
        boolean xmaxExcl = true;
        if(!Double.isNaN(max) && max<xmax) {
            xmax = max;
            xmaxExcl = maxIncl;
        }
        if(!Double.isNaN(other.max) && ((other.maxIncl && other.max<xmax) || other.max<=xmax)) {
            xmax = other.max;
            xmaxExcl = other.maxIncl;
        }
        
        if(Double.isInfinite(xmax)) {
            xmax = Double.NaN;
        }
        
        return new FloatRange(xmin, xmax, xminExcl, xmaxExcl);
    }

    @Override
    public String toString() {
        return (minIncl?"[":"(") + (Double.isNaN(min)?"-inf":min) + "," + (Double.isNaN(max)?"+inf":max) + (maxIncl?"]":")");
    }
}
