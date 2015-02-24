package org.yamcs.xtceproc;

import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.IntegerArgumentType;

public class ArgumentTypeProcessor {
	
	public static Object decalibrate(ArgumentType atype, Object v) {
		if (atype instanceof EnumeratedArgumentType) {
			return decalibrateEnumerated((EnumeratedArgumentType) atype, v);
		} else if (atype instanceof IntegerArgumentType) {
			return decalibrateInteger((IntegerArgumentType) atype, v);
		} else if (atype instanceof FloatArgumentType) {
			return decalibrateFloat((FloatArgumentType) atype, v);
		} else {
			throw new IllegalArgumentException("decalibration for "+atype+" not implemented");
		}
	}

	private static Object decalibrateFloat(FloatArgumentType atype, Object v) {

		return null;
	}

	private static Object decalibrateInteger(IntegerArgumentType atype, Object v) {
		return null;
		
	}

	private static Object decalibrateEnumerated(EnumeratedArgumentType atype, Object v) {
		return null;
		
	}
}
