package org.yamcs.xtce;

import java.util.Arrays;
import java.util.List;

/**
 * A calibration type where a segmented line in a raw vs calibrated plane is described using a set of points.  
 * Raw values are converted to calibrated values by finding a position on the line corresponding  to the raw value. 
 * The algorithm triggers on the input parameter.
 *
 */
public class SplineCalibrator extends Calibrator {
    private static final long serialVersionUID = 200706050819L;
    SplinePoint[] points;

    public SplineCalibrator(List<SplinePoint> points) {
        this.points=points.toArray(new SplinePoint[0]);
        Arrays.sort(this.points);
    }

    @Override
    public String toString() {
        return "SplineCalibrator"+Arrays.toString(points);
    }

    public SplinePoint[] getPoints() {
        return points;
    }
}
