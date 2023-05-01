package org.yamcs.mdb;

import java.util.Map;

import org.yamcs.logging.Log;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.EnumeratedValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.UnsignedLong;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AbsoluteTimeDataType;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.ArrayDataType;
import org.yamcs.xtce.BinaryDataType;
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.EnumeratedDataType;
import org.yamcs.xtce.FloatDataType;
import org.yamcs.xtce.IntegerDataType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.StringDataType;
import org.yamcs.xtce.ValueEnumeration;

public class DataTypeProcessor {

    private static final Log log = new Log(DataTypeProcessor.class);

    /**
     * converts a (boxed) java value from the XTCE type to Yamcs Value
     * 
     * @param type
     * @return
     */
    public static Value getValueForType(DataType type, Object o) {
        Value v;
        if (type instanceof IntegerDataType) {
            IntegerDataType intType = (IntegerDataType) type;
            Long l = (Long) o;
            if (intType.getSizeInBits() <= 32) {
                if (intType.isSigned()) {
                    v = ValueUtility.getSint32Value(l.intValue());
                } else {
                    v = ValueUtility.getUint32Value(l.intValue());
                }
            } else {
                if (intType.isSigned()) {
                    v = ValueUtility.getSint64Value(l);
                } else {
                    v = ValueUtility.getUint64Value(l);
                }
            }
        } else if (type instanceof FloatDataType) {
            FloatDataType fdt = (FloatDataType) type;

            Double d = (Double) o;

            if (fdt.getSizeInBits() <= 32) {
                v = ValueUtility.getFloatValue(d.floatValue());
            } else {
                v = ValueUtility.getDoubleValue(d);
            }
        } else if (type instanceof StringDataType) {
            v = ValueUtility.getStringValue((String) o);
        } else if (type instanceof BinaryDataType) {
            v = ValueUtility.getBinaryValue((byte[]) o);
        } else if (type instanceof EnumeratedDataType) {
            EnumeratedDataType edt = (EnumeratedDataType) type;
            ValueEnumeration ve = edt.enumValue((String) o);
            if (ve == null) { // EnumeratedDataType supports ranges (i.e. multiple integer values mapped to the same
                              // label), we cannot create a EnumeratedValue for those so we let it string
                v = ValueUtility.getStringValue((String) o);
            } else {
                v = ValueUtility.getEnumeratedValue(ve.getValue(), ve.getLabel());
            }
        } else if (type instanceof BooleanDataType) {
            v = ValueUtility.getBooleanValue((Boolean) o);
        } else if (type instanceof AbsoluteTimeDataType) {
            v = ValueUtility.getTimestampValue(TimeEncoding.parse((String) o));
        } else if (type instanceof AggregateDataType) {
            v = getAggregateValue((AggregateDataType) type, (Map<String, Object>) o);
        } else if (type instanceof ArrayDataType) {
            v = getArrayValue((ArrayDataType) type, (Object[]) o);
        } else {
            throw new IllegalArgumentException("Cannot parse values of type " + type);
        }
        return v;
    }

    /**
     * Converts a Value to suit the type engineering value
     * <p>
     * Throws {@link IllegalArgumentException} if cannot convert.
     * 
     * @param type
     *            - target type
     * @param v
     *            - value to be converted
     * @return
     */
    public static Value convertEngValueForType(DataType type, Value v) {
        if (type instanceof IntegerDataType) {
            return convertToIntegerType((IntegerDataType) type, v);
        } else if (type instanceof FloatDataType) {
            return convertToFloatType((FloatDataType) type, v);
        } else if (type instanceof StringDataType) {
            return convertToStringType((StringDataType) type, v);
        } else if (type instanceof BinaryDataType) {
            return convertToBinaryType((BinaryDataType) type, v);
        } else if (type instanceof EnumeratedDataType) {
            return convertToEnumeratedType((EnumeratedDataType) type, v);
        } else if (type instanceof BooleanDataType) {
            return convertToBooleanType((BooleanDataType) type, v);
        } else if (type instanceof AbsoluteTimeDataType) {
            return convertToAbsoluteTimeType((AbsoluteTimeDataType) type, v);
        } else if (type instanceof AggregateDataType) {
            return convertToAggregateType((AggregateDataType) type, v);
        } else if (type instanceof ArrayDataType) {
            return convertToArrayType((ArrayDataType) type, v);
        } else {
            throw new IllegalStateException("Unknown type " + type);
        }
    }

    private static Value convertToArrayType(ArrayDataType type, Value v) {
        if (v.getType() == Type.STRING) {
            return DataTypeProcessor.getValueForType(type, type.convertType(v.getStringValue()));
        } else if (v.getType() == Type.ARRAY) {
            ArrayValue arrayValue = (ArrayValue) v;
            ArrayValue r = new ArrayValue(arrayValue.getDimensions(), v.getType());
            for (int i = 0; i < arrayValue.flatLength(); i++) {
                Value ev = convertEngValueForType(type.getElementType(), arrayValue.getElementValue(i));
                r.setElementValue(i, ev);
            }
            return r;
        } else {
            throw new IllegalArgumentException(
                    "Cannot convert " + v.getType() + " to an array type " + type.getName());
        }
    }

    private static Value convertToAggregateType(AggregateDataType type, Value v) {
        if (v.getType() == Type.STRING) {
            return DataTypeProcessor.getValueForType(type, type.convertType(v.getStringValue()));
        } else if (v.getType() == Type.AGGREGATE) {
            AggregateValue aggrValue = (AggregateValue) v;
            AggregateValue r = new AggregateValue(type.getMemberNames());

            for (Member m : type.getMemberList()) {
                int idx = aggrValue.getMemberIndex(m.getName());
                if (idx == -1) {
                    throw new IllegalArgumentException(
                            "Cannot convert value '" + v + "' to aggregate type " + type.getName()
                                    + ": no value for member " + m.getName());
                }
                Value mv = aggrValue.getMemberValue(idx);
                Value nv = convertEngValueForType(m.getType(), mv);
                r.setMemberValue(m.getName(), nv);
            }
            return r;
        } else {
            throw new IllegalArgumentException(
                    "Cannot convert " + v.getType() + " to an aggregate type " + type.getName());
        }

    }

    private static Value convertToAbsoluteTimeType(AbsoluteTimeDataType type, Value v) {
        switch (v.getType()) {
        case TIMESTAMP:
            return v;
        case STRING:
            return ValueUtility.getTimestampValue(TimeEncoding.parse(v.getStringValue()));
        default:
            throw new IllegalArgumentException(MSG_CANT_CONVERT(v.getType(), type));
        }
    }

    private static Value convertToBooleanType(BooleanDataType type, Value v) {
        switch (v.getType()) {
        case BOOLEAN:
            return v;
        case SINT32:
            return ValueUtility.getBooleanValue(v.getSint32Value() != 0);
        case SINT64:
            return ValueUtility.getBooleanValue(v.getSint64Value() != 0);
        case UINT32:
            return ValueUtility.getBooleanValue(v.getUint32Value() != 0);
        case UINT64:
            return ValueUtility.getBooleanValue(v.getUint64Value() != 0);
        case STRING:
            String s = v.getStringValue();
            if (type.getOneStringValue().equals(s)) {
                return ValueUtility.getBooleanValue(true);
            } else if (type.getZeroStringValue().equals(s)) {
                return ValueUtility.getBooleanValue(false);
            } else if ("true".equalsIgnoreCase(s)) { // TODO remove eventually
                log.warn("Boolean conversion which does not use oneStringValue/zeroStringValue");
                return ValueUtility.getBooleanValue(true);
            } else if ("false".equalsIgnoreCase(s)) { // TODO remove eventually
                log.warn("Boolean conversion which does not use oneStringValue/zeroStringValue");
                return ValueUtility.getBooleanValue(false);
            } else {
                throw new IllegalArgumentException(String.format(
                        "Cannot convert '%s' to boolean. Expecting either '%s' or '%s'",
                        v, type.getOneStringValue(), type.getZeroStringValue()));
            }
        default:
            throw new IllegalArgumentException(MSG_CANT_CONVERT(v.getType(), type));
        }
    }

    private static EnumeratedValue convertToEnumeratedType(EnumeratedDataType type, Value v) {
        switch (v.getType()) {
        case SINT32:
            return ValueUtility.getEnumeratedValue(v.getSint32Value(), type.calibrate(v.getSint32Value()));
        case SINT64:
            return ValueUtility.getEnumeratedValue(v.getSint64Value(), type.calibrate(v.getSint64Value()));
        case UINT32:
            return ValueUtility.getEnumeratedValue(v.getUint32Value(), type.calibrate(v.getUint32Value()));
        case UINT64:
            return ValueUtility.getEnumeratedValue(v.getUint64Value(), type.calibrate(v.getUint64Value()));
        case STRING:
            ValueEnumeration ve = type.enumValue(v.getStringValue());
            if (ve == null) {
                throw new IllegalArgumentException(
                        "Cannot convert " + v + " to " + type.getName() + "; it is not a valid label");
            }
            return ValueUtility.getEnumeratedValue(ve.getValue(), ve.getLabel());
        default:
            throw new IllegalArgumentException(MSG_CANT_CONVERT(v.getType(), type));
        }
    }

    private static Value convertToBinaryType(BinaryDataType type, Value v) {
        if (v.getType() == Type.BINARY) {
            return v;
        }
        if (v.getType() == Type.STRING) {
            byte[] b = StringConverter.hexStringToArray(v.getStringValue());
            return ValueUtility.getBinaryValue(b);
        } else {
            throw new IllegalArgumentException(MSG_CANT_CONVERT(v.getType(), type));
        }
    }

    private static Value convertToStringType(StringDataType type, Value v) {
        if (v.getType() == Type.STRING) {
            return v;
        } else {
            return ValueUtility.getStringValue(v.toString());
        }
    }

    private static Value convertToFloatType(FloatDataType type, Value v) {
        switch (v.getType()) {
        case DOUBLE:
            return ValueUtility.getFloatValue(type.getSizeInBits(), v.getDoubleValue());
        case FLOAT:
            return ValueUtility.getFloatValue(type.getSizeInBits(), v.getFloatValue());
        case SINT32:
            return ValueUtility.getFloatValue(type.getSizeInBits(), v.getSint32Value());
        case SINT64:
            return ValueUtility.getFloatValue(type.getSizeInBits(), v.getSint64Value());
        case UINT32:
            return ValueUtility.getFloatValue(type.getSizeInBits(), v.getUint32Value() & Long.MAX_VALUE);
        case UINT64:
            return ValueUtility.getFloatValue(type.getSizeInBits(), UnsignedLong.toDouble(v.getUint64Value()));
        default:
            throw new IllegalArgumentException(MSG_CANT_CONVERT(v.getType(), type));
        }
    }

    private static Value convertToIntegerType(IntegerDataType type, Value v) {
        switch (v.getType()) {
        case SINT32:
            int sintValue = v.getSint32Value();
            if (type.isSigned()) {
                if (type.getSizeInBits() < signedSizeInBits(sintValue)) {
                    throw new IllegalArgumentException(MSG_CANT_FIT(v, type));
                }
            } else {
                if (sintValue < 0) {
                    throw new IllegalArgumentException(
                            "Cannot convert negative value " + sintValue + " to unsigned type " + type.getName());
                }
                if (type.getSizeInBits() < unsignedSizeInBits(sintValue)) {
                    throw new IllegalArgumentException(MSG_CANT_FIT(v, type));
                }
            }

            return ValueUtility.getIntValue(type.getSizeInBits(), type.isSigned(), sintValue);
        case SINT64:
            long slongValue = v.getSint64Value();
            if (type.isSigned()) {
                if (type.getSizeInBits() < signedSizeInBits(slongValue)) {
                    throw new IllegalArgumentException(MSG_CANT_FIT(v, type));
                }
            } else {
                if (slongValue < 0) {
                    throw new IllegalArgumentException(
                            "Cannot convert negative value " + slongValue + " to unsigned type " + type.getName());
                }
                if (type.getSizeInBits() < unsignedSizeInBits(slongValue)) {
                    throw new IllegalArgumentException(MSG_CANT_FIT(v, type));
                }
            }
            return ValueUtility.getIntValue(type.getSizeInBits(), type.isSigned(), slongValue);
        case UINT32:
            int uintValue = v.getUint32Value();
            if (type.getSizeInBits() < unsignedSizeInBits(uintValue) - 1) {
                throw new IllegalArgumentException(MSG_CANT_FIT(v, type));
            }
            return ValueUtility.getIntValue(type.getSizeInBits(), type.isSigned(), uintValue);
        case UINT64:
            long ulongValue = v.getUint64Value();
            if (type.getSizeInBits() < unsignedSizeInBits(ulongValue) - 1) {
                throw new IllegalArgumentException(MSG_CANT_FIT(v, type));
            }
            return ValueUtility.getIntValue(type.getSizeInBits(), type.isSigned(), ulongValue);

        case STRING:
        default:
            throw new IllegalArgumentException(MSG_CANT_CONVERT(v.getType(), type));

        }
    }

    private static AggregateValue getAggregateValue(AggregateDataType aggregateDataType, Map<String, Object> o) {
        AggregateValue v = new AggregateValue(aggregateDataType.getMemberNames());
        for (Member m : aggregateDataType.getMemberList()) {
            Object o1 = o.get(m.getName());
            if (o1 == null) {
                throw new IllegalArgumentException("No value provided for member '" + m.getName());
            }
            v.setMemberValue(m.getName(), getValueForType(m.getType(), o1));
        }

        if (v.numMembers() > 0) {
            return v;
        } else {
            return null;
        }
    }

    private static Value getArrayValue(ArrayDataType type, Object[] o) {
        if (type.getNumberOfDimensions() != 1) {
            throw new UnsupportedOperationException("TODO");
        }

        ArrayValue v = new ArrayValue(new int[] { o.length }, type.getElementType().getValueType());
        for (int i = 0; i < o.length; i++) {
            v.setElementValue(i, getValueForType(type.getElementType(), o[i]));
        }
        return v;
    }

    static public final int unsignedSizeInBits(long v) {
        return 64 - Long.numberOfLeadingZeros(v);
    }

    static public final int signedSizeInBits(long v) {
        return 65 - (v < 0 ? Long.numberOfLeadingZeros(~v) : Long.numberOfLeadingZeros(v));
    }

    static final String MSG_CANT_CONVERT(Type type1, DataType type2) {
        return "Cannot convert " + type1 + " to " + type2.getName();
    }

    static final String MSG_CANT_FIT(Value v, IntegerDataType type) {
        return "Cannot fit " + v + " in " + type.getSizeInBits()
                + " bits required for data type " + type.getName();
    }
}
