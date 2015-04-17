package org.yamcs.xtceproc;

import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerValidRange;

public class ValidRangeChecker {
	
	/**
	 * checks that x is in the range and returns true if it is and false if it's not
	 * @param fvr
	 * @param d
	 * @return
	 */
	public static boolean checkFloatRange(FloatValidRange fvr, double x) {
		return (x>=fvr.getMinInclusive() && x<=fvr.getMaxInclusive());
	}
	
	
	/**
	 * checks that x is in the range and returns true if it is and false if it's not
	 * @param fvr
	 * @param d
	 * @return
	 */
	public static boolean checkIntegerRange(IntegerValidRange vr, long x) {
		return (x>=vr.getMinInclusive() && x<=vr.getMaxInclusive());
	}
}
