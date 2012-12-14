package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A calibration type where a segmented line in a raw vs calibrated plane is described using a set of points.  
 * Raw values are converted to calibrated values by finding a position on the line corresponding  to the raw value. 
 * The algorithm triggers on the input parameter.
 *
 */
public class SplineCalibrator extends Calibrator {
	private static final long serialVersionUID = 200706050819L;
	SplinePoint[] points;

	public SplineCalibrator(ArrayList<SplinePoint> points) {
		this.points=points.toArray(new SplinePoint[0]);
		Arrays.sort(this.points);
	}
	@Override
	public Double calibrate(double d) {
		double val=0;

		int i=0,j=0;
		for(i=0;i<points.length;i++) {
			if(points[i].raw>=d) break;
		}
		if(i==0) {
			j=0;i=1;
		} else if(i==points.length) {
			j=points.length-2;
			i=points.length-1;
		} else {
			j=i-1;
		}
		double a1=points[i].raw; double b1=points[i].calibrated;
		double a2=points[j].raw; double b2=points[j].calibrated;
		val=((b1-b2)*d+(a1*b2-b1*a2))/(a1-a2);
		return val;
	}
	
	@Override
    public String toString() {
		return "SplineCalibrator"+Arrays.toString(points);
	}
}
