package org.yamcs.xtceproc;

import java.util.Map;

import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.Value;
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

public class DataTypeProcessor {

    /**
     * converts the value from the XTCE type to Yamcs Value
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
            v = ValueUtility.getStringValue((String) o);
        } else if (type instanceof BooleanDataType) {
            v = ValueUtility.getBooleanValue((Boolean) o);
        } else if (type instanceof AbsoluteTimeDataType) {
            v = ValueUtility.getTimestampValue((Long) o);
        } else if (type instanceof AggregateDataType) {
            v = getAggregateValue((AggregateDataType) type, (Map<String, Object>) o);
        } else if (type instanceof ArrayDataType) {
            v = getArrayValue((ArrayDataType) type, (Object[]) o);
        } else {
            throw new IllegalArgumentException("Cannot parse values of type " + type);
        }
        return v;
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

}
