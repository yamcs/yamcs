package org.yamcs.mdb;

/**
 * Used as a sub-calibrator in {@link NumericCalibratorProc} to convert double to double
 * <p>
 * Before Yamcs 5.12 and as per XTCE, this was the only calibration possible. Starting with Yamcs 5.12 we allow more
 * general raw to engineering conversion that can convert any type to any type. Therefore this has been changed into a
 * sub-calibrator
 */
public interface NumericCalibrator {
    double calibrate(double v);
}
