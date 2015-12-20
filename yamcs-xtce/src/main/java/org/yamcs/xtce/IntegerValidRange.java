package org.yamcs.xtce;
/**
 * XTCE: Holds an integer range and flag denoting whether the range is calculated on the value using the source data type or the destination data type.
 */
public class IntegerValidRange extends IntegerRange {
    /**
     * Ranges are applied to the raw source DataEncoding data type or against the calibrated or converted destination data type
     */
    boolean validRangeAppliesToCalibrated=false;



    public IntegerValidRange(long minInclusive, long maxInclusive) {
        super(minInclusive, maxInclusive);
    }

    public boolean isValidRangeAppliesToCalibrated() {
        return validRangeAppliesToCalibrated;
    }

    public void setValidRangeAppliesToCalibrated(boolean validRangeAppliesToCalibrated) {
        this.validRangeAppliesToCalibrated = validRangeAppliesToCalibrated;
    }


}
