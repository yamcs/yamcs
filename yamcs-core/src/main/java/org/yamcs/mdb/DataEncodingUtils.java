package org.yamcs.mdb;

import org.yamcs.parameter.StringValue;
import org.yamcs.parameter.UInt64Value;
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;

/**
 * creates raw values according to the data encoding used
 *
 */
public class DataEncodingUtils {
    /**
     * converts longValue to a raw Value suitable for data encoding. It truncates (remove the most significants bits)
     * the value to fit.
     */
    static public Value getRawIntegerValue(IntegerDataEncoding ide, long longValue) {
        int sizeInBits = ide.getSizeInBits();

        if (sizeInBits > 0) {// sizeInBits = -1 used for integers with string encoding
            // limit the value to the number of defined bits
            longValue = longValue & (-1l >>> (64 - sizeInBits));
        }

        if (sizeInBits > 0 && sizeInBits <= 32) {
            if (ide.getEncoding() == Encoding.UNSIGNED) {
                return ValueUtility.getUint32Value((int) longValue);
            } else {
                return ValueUtility.getSint32Value((int) longValue);
            }
        } else {
            if (ide.getEncoding() == Encoding.UNSIGNED) {
                return ValueUtility.getUint64Value(longValue);
            } else {
                return ValueUtility.getSint64Value(longValue);
            }
        }
    }

    static public Value getRawIntegerValue(IntegerDataEncoding ide, Object value) {
        long longValue;
        if (value instanceof Number) {
            longValue = ((Number) value).longValue();
        } else {
            return null;
        }
        return getRawIntegerValue(ide, longValue);
    }

    static public Value getRawIntegerValue(IntegerDataEncoding ide, Value value) {
        if (value instanceof UInt64Value) {
            if (ide.getEncoding() == Encoding.UNSIGNED && ide.getSizeInBits() == 64) {
                return value;
            } else {
                return getRawIntegerValue(ide, value.getUint64Value());
            }
        } else {
            return getRawIntegerValue(ide, value.toLong());
        }
    }

    static public Value getRawFloatValue(FloatDataEncoding fde, double value) {
        if (fde.getSizeInBits() <= 32
                && (fde.getEncoding() == FloatDataEncoding.Encoding.IEEE754_1985
                || fde.getEncoding() == FloatDataEncoding.Encoding.STRING)) {
            return ValueUtility.getFloatValue((float) value);
        } else {
            return ValueUtility.getDoubleValue(value);
        }
    }

    static public Value getRawFloatValue(FloatDataEncoding fde, Object value) {
        double doubleValue;
        if (value instanceof Number) {
            doubleValue = ((Number) value).doubleValue();
        } else {
            return null;
        }
        return getRawFloatValue(fde, doubleValue);
    }

    static public Value rawRawStringValue(Value engValue) {
        if (engValue instanceof StringValue) {
            return engValue;
        } else {
            return ValueUtility.getStringValue(engValue.toString());
        }
    }

}
