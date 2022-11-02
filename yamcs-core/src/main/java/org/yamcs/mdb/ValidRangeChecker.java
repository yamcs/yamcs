package org.yamcs.mdb;

import org.yamcs.parameter.UInt64Value;
import org.yamcs.parameter.Value;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerValidRange;

public class ValidRangeChecker {

    /**
     * checks that x is in the range and returns true if it is and false if it's not
     * 
     * @param fvr
     * @param x
     * @return
     */
    public static boolean checkFloatRange(FloatValidRange fvr, double x) {
        return fvr.inRange(x) == 0;
    }

    /**
     * checks that x is in the range and returns true if it is and false if it's not
     * 
     * @param vr
     *            - range to check against
     * @param x
     * @return
     */
    public static boolean checkIntegerRange(IntegerValidRange vr, long x) {
        return (x >= vr.getMinInclusive() && x <= vr.getMaxInclusive());
    }

    public static boolean checkUnsignedIntegerRange(IntegerValidRange vr, long x) {
        return (Long.compareUnsigned(x, vr.getMinInclusive()) >= 0
                && Long.compareUnsigned(x, vr.getMaxInclusive()) <= 0);
    }

    /**
     * Checks if the value is within range. If the value is not of numeric type returns false.
     */
    public static boolean checkFloatRange(FloatValidRange fvr, Value v) {
        switch (v.getType()) {
        case SINT32:
            return checkFloatRange(fvr, v.getSint32Value());
        case SINT64:
            return checkFloatRange(fvr, v.getSint64Value());
        case UINT32:
            return checkFloatRange(fvr, v.getUint32Value() & 0xFFFFFFFFL);
        case UINT64:
            return checkFloatRange(fvr, UInt64Value.unsignedAsDouble(v.getUint64Value()));
        case FLOAT:
            return checkFloatRange(fvr, v.getFloatValue());
        case DOUBLE:
            return checkFloatRange(fvr, v.getDoubleValue());
        default:
            return false;
        }
    }

    /**
     * Checks if the value is within range. If the value is not of numeric type returns false.
     */
    public static boolean checkIntegerRange(IntegerValidRange ivr, Value v) {
        switch (v.getType()) {
        case SINT32:
            return checkIntegerRange(ivr, v.getSint32Value());
        case SINT64:
            return checkIntegerRange(ivr, v.getSint64Value());
        case UINT32:
            return checkUnsignedIntegerRange(ivr, v.getUint32Value() & 0xFFFFFFFFL);
        case UINT64:
            return checkUnsignedIntegerRange(ivr, v.getUint64Value());
        case FLOAT:
            return checkIntegerRange(ivr, (long)v.getFloatValue());
        case DOUBLE:
            return checkIntegerRange(ivr, (long)v.getDoubleValue());
        default:
            return false;
        }
    }
}
