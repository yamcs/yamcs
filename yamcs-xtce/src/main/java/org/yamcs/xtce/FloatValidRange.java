package org.yamcs.xtce;

/**
 * Holds the min/max of a ValidRange for floating point.
 * @author nm
 *
 */
public class FloatValidRange extends FloatRange {
	/**
	 * The range specified applies to the destination data type if true, 
	 * or the raw source data type if false. The default is false and reflects the more likely scenario of checking raw values before conversion or calibration in telemetry.
	 */
	boolean validRangeAppliesToCalibrated;
	
	public FloatValidRange(double minInclusive, double maxInclusive) {
		super(minInclusive, maxInclusive);
	}

}
