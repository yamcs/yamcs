package org.yamcs.xtce;

import java.io.Serializable;

/**
 * a spline is a set on points from which a curve may be drawn to interpolate raw to calibrated values
 * 
 * @author nm
 *
 */
public class SplinePoint implements Serializable, Comparable<SplinePoint>{
    private static final long serialVersionUID = 200706050619L;
    public SplinePoint(double raw, double calibrated) {
        this.raw=raw;
        this.calibrated=calibrated;
    }
    double raw;
    double calibrated;


    @Override
    public int compareTo(SplinePoint sp) {
        return Double.compare(raw, sp.raw);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof SplinePoint)) return false;
        SplinePoint sp=(SplinePoint)o;
        return Double.compare(raw, sp.raw)==0;
    }

    @Override
    public int hashCode() {
        return Double.valueOf(raw).hashCode();
    }

    @Override
    public String toString() {
        return "("+raw+","+calibrated+")";
    }
}