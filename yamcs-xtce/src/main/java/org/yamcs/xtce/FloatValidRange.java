package org.yamcs.xtce;

import org.yamcs.xtce.util.DoubleRange;

/**
 * Holds the min/max of a ValidRange for floating point.
 * 
 * @author nm
 *
 */
public class FloatValidRange extends DoubleRange {
    private static final long serialVersionUID = 2L;

    /**
     * The range specified applies to the destination data type if true, or the raw source data type if false. The
     * default is false and reflects the more likely scenario of checking raw values before conversion or calibration in
     * telemetry.
     */
    private boolean validRangeAppliesToCalibrated;

    public FloatValidRange(double minInclusive, double maxInclusive) {
        super(minInclusive, maxInclusive);
    }

    public FloatValidRange(DoubleRange range) {
        super(range);
    }

    public boolean isValidRangeAppliesToCalibrated() {
        return validRangeAppliesToCalibrated;
    }

    public void setValidRangeAppliesToCalibrated(boolean validRangeAppliesToCalibrated) {
        this.validRangeAppliesToCalibrated = validRangeAppliesToCalibrated;
    }

}
