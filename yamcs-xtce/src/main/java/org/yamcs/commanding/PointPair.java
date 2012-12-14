package org.yamcs.commanding;

import java.io.Serializable;

/**
 * Used in TC decalibration. TODO: replace with an XTCE equivalent
 * @author nm
 *
 */
public class PointPair implements Serializable, Comparable<PointPair> {
	private static final long serialVersionUID = 200706050619L;
	final double x;
	final double y;

	public PointPair(double x, double y) {
		this.x=x;
		this.y=y;
	}
	public String toString() {
		return "("+x+","+y+")";
	}
	public int compareTo(PointPair p) {
		return Double.compare(x, p.x);
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof PointPair)) return false;
		PointPair p=(PointPair)o;
		return Double.compare(x, p.x)==0;
	}
	@Override
	public int hashCode() {
		return Double.valueOf(x).hashCode();
	}
	
}
