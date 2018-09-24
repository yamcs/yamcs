package org.yamcs.xtceproc;

import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerRange;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.ValueEnumeration;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.UnsignedLongs;

/**
 * Contains various static methods to help work with XTCE {@link ParameterType}
 * 
 * @author nm
 *
 */
public class ParameterTypeUtils {
    static Multimap<Class<? extends ParameterType>, org.yamcs.protobuf.Yamcs.Value.Type> allowedAssignments = new ImmutableSetMultimap.Builder<Class<? extends ParameterType>, org.yamcs.protobuf.Yamcs.Value.Type>()
            .putAll(BinaryParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.BINARY)
            .putAll(BooleanParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.BOOLEAN)
            .putAll(EnumeratedParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.STRING)
            .putAll(FloatParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.FLOAT,
                    org.yamcs.protobuf.Yamcs.Value.Type.DOUBLE)
            .putAll(IntegerParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.UINT32,
                    org.yamcs.protobuf.Yamcs.Value.Type.SINT32, org.yamcs.protobuf.Yamcs.Value.Type.SINT64,
                    org.yamcs.protobuf.Yamcs.Value.Type.UINT64)
            .putAll(StringParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.STRING)
            .build();

    /**
     * Checks that a value can be assigned to a parameter as enginnering value
     * Throws an IllegalArgumentException if not
     *
     * @param p
     * @param engValue
     */
    public static void checkEngValueAssignment(Parameter p, Value engValue) {
        ParameterType ptype = p.getParameterType();
        if (!allowedAssignments.containsEntry(ptype.getClass(), engValue.getType())) {
            throw new IllegalArgumentException(
                    "Cannot assign " + ptype.getTypeAsString() + " from " + engValue.getType());
        }
    }

    public static Value parseString(ParameterType type, String paramValue) {
        Value v;
        if (type instanceof IntegerParameterType) {
            IntegerParameterType intType = (IntegerParameterType) type;
            if (intType.isSigned()) {
                long l = Long.decode(paramValue);
                IntegerValidRange vr = ((IntegerArgumentType) type).getValidRange();
                if (vr != null) {
                    if (!ValidRangeChecker.checkIntegerRange(vr, l)) {
                        throw new IllegalArgumentException(
                                "Value " + l + " is not in the range required for the type " + type);
                    }
                }
                v = ValueUtility.getSint64Value(l);
            } else {
                long l = UnsignedLongs.decode(paramValue);
                IntegerValidRange vr = ((IntegerParameterType) type).getValidRange();
                if (vr != null) {
                    if (!ValidRangeChecker.checkUnsignedIntegerRange(vr, l)) {
                        throw new IllegalArgumentException(
                                "Value " + l + " is not in the range required for the type " + type);
                    }
                }
                v = ValueUtility.getUint64Value(l);
            }

        } else if (type instanceof FloatParameterType) {
            double d = Double.parseDouble(paramValue);
            FloatValidRange vr = ((FloatParameterType) type).getValidRange();
            if (vr != null) {
                if (!ValidRangeChecker.checkFloatRange(vr, d)) {
                    throw new IllegalArgumentException(
                            "Value " + d + " is not in the range required for the type " + type);
                }
            }
            v = ValueUtility.getDoubleValue(d);
        } else if (type instanceof StringParameterType) {
            v = ValueUtility.getStringValue(paramValue);
            IntegerRange r = ((StringParameterType) type).getSizeRangeInCharacters();

            if (r != null) {
                int length = paramValue.length();
                if (length < r.getMinInclusive()) {
                    throw new IllegalArgumentException("Value " + paramValue + " supplied for parameter fo type " + type
                            + " does not satisfy minimum length of " + r.getMinInclusive());
                }
                if (length > r.getMaxInclusive()) {
                    throw new IllegalArgumentException("Value " + paramValue + " supplied for parameter fo type " + type
                            + " does not satisfy maximum length of " + r.getMaxInclusive());
                }
            }

        } else if (type instanceof BinaryParameterType) {
            byte[] b = StringConverter.hexStringToArray(paramValue);
            v = ValueUtility.getBinaryValue(b);
        } else if (type instanceof EnumeratedArgumentType) {
            EnumeratedArgumentType enumType = (EnumeratedArgumentType) type;
            List<ValueEnumeration> vlist = enumType.getValueEnumerationList();
            boolean found = false;
            for (ValueEnumeration ve : vlist) {
                if (ve.getLabel().equals(paramValue)) {
                    found = true;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Value '" + paramValue
                        + "' supplied for enumeration argument cannot be found in enumeration list " + vlist);
            }
            v = ValueUtility.getStringValue(paramValue);
        } else if (type instanceof BooleanParameterType) {
            boolean b = Boolean.parseBoolean(paramValue);
            v = ValueUtility.getBooleanValue(b);
        } else {
            throw new IllegalArgumentException("Cannot parse values of type " + type);
        }
        return v;
    }

    /**
     * Returns the nominal Value.Type of a parameter of the given XTCE parameter type definition
     * 
     * @param ptype
     *            - the parameter type
     * @return
     */
    public static org.yamcs.protobuf.Yamcs.Value.Type getEngType(ParameterType ptype) {
        if (ptype instanceof IntegerParameterType) {
            IntegerParameterType ipt = (IntegerParameterType) ptype;
            if (ipt.getSizeInBits() <= 32) {
                if (ipt.isSigned()) {
                    return Type.SINT32;
                } else {
                    return Type.UINT32;
                }
            } else {
                if (ipt.isSigned()) {
                    return Type.SINT64;
                } else {
                    return Type.UINT64;
                }
            }
        } else if (ptype instanceof FloatParameterType) {
            FloatParameterType fpt = (FloatParameterType) ptype;
            if (fpt.getSizeInBits() <= 32) {
                return Type.FLOAT;
            } else {
                return Type.DOUBLE;
            }
        } else if (ptype instanceof BooleanParameterType) {
            return Type.BOOLEAN;
        } else if (ptype instanceof BinaryParameterType) {
            return Type.BINARY;
        } else if (ptype instanceof StringParameterType) {
            return Type.STRING;
        } else if (ptype instanceof EnumeratedParameterType) {
            return Type.STRING;
        } else if (ptype instanceof AbsoluteTimeParameterType) {
            return Type.TIMESTAMP;
        } else {
            throw new IllegalStateException("Unknonw parameter type '" + ptype + "'");
        }
    }

    /**
     * Returns a Value corresponding to the java object and the parameter type or null if the parameter cannot be
     * converted
     * 
     * Note that the operation may involve some casting and loss of precision (e.g. from long to int or double to float)
     * 
     * @param ptype
     * @param value
     * @return
     */
    public static Value getEngValue(ParameterType ptype, Object value) {
        if (ptype instanceof IntegerParameterType) {
            return getEngIntegerValue((IntegerParameterType) ptype, value);
        } else if (ptype instanceof FloatParameterType) {
            return getEngFloatValue((FloatParameterType) ptype, value);
        } else if (ptype instanceof StringParameterType) {
            if (value instanceof String) {
                return ValueUtility.getStringValue((String) value);
            } else {
                return null;
            }
        } else if (ptype instanceof BooleanParameterType) {
            if (value instanceof Boolean) {
                return ValueUtility.getBooleanValue((Boolean) value);
            } else {
                return null;
            }
        } else if (ptype instanceof BinaryParameterType) {
            if (value instanceof byte[]) {
                return ValueUtility.getBinaryValue((byte[]) value);
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException("Unknown parameter type '" + ptype + "'");
        }
    }

    static Value getEngIntegerValue(IntegerParameterType ptype, Object value) {
        long longValue;
        if (value instanceof Number) {
            longValue = ((Number) value).longValue();
        } else {
            return null;
        }
        if (ptype.getSizeInBits() <= 32) {
            if (ptype.isSigned()) {
                return ValueUtility.getSint32Value((int) longValue);
            } else {
                return ValueUtility.getUint32Value((int) longValue);
            }
        } else {
            if (ptype.isSigned()) {
                return ValueUtility.getSint64Value(longValue);
            } else {
                return ValueUtility.getUint64Value(longValue);
            }
        }
    }

    static Value getEngFloatValue(FloatParameterType ptype, Object value) {
        double doubleValue;
        if (value instanceof Number) {
            doubleValue = ((Number) value).doubleValue();
        } else {
            return null;
        }
        if (ptype.getSizeInBits() <= 32) {
            return ValueUtility.getFloatValue((float) doubleValue);
        } else {
            return ValueUtility.getDoubleValue(doubleValue);
        }
    }

}
