package org.yamcs.mdb;

import java.util.Arrays;

import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;

/**
 * A calibration type where a segmented line in a raw vs calibrated plane is described using a set of points.
 * Raw values are converted to calibrated values by finding a position on the line corresponding to the raw value.
 * The algorithm triggers on the input parameter.
 *
 */
public class SplineCalibratorProc implements CalibratorProc {
    SplinePoint[] points;

    public SplineCalibratorProc(SplineCalibrator c) {
        this.points = c.getPoints();
    }

    @Override
    public double calibrate(double d) {
        double val;

        int i = 0, j = 0;
        for (i = 0; i < points.length; i++) {
            if (points[i].getRaw() >= d)
                break;
        }
        if (i == 0) {
            j = 0;
            i = 1;
        } else if (i == points.length) {
            j = points.length - 2;
            i = points.length - 1;
        } else {
            j = i - 1;
        }
        double a1 = points[i].getRaw();
        double b1 = points[i].getCalibrated();

        double a2 = points[j].getRaw();
        double b2 = points[j].getCalibrated();

        val = ((b1 - b2) * d + (a1 * b2 - b1 * a2)) / (a1 - a2);
        return val;
    }

    @Override
    public String toString() {
        return "SplineCalibrator" + Arrays.toString(points);
    }
}
