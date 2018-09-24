package org.yamcs.xtceproc;

import org.yamcs.parameter.AggregateValue;
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
     * returns the initial(default) value for the type or null if no value has been set 
     * @param type
     * @return
     */
    public static Value getInitialValue(DataType type) {
        Value v;
        if(type instanceof IntegerDataType) {
            IntegerDataType intType = (IntegerDataType) type;
            Long l = intType.getInitialValue();
            if(l==null) {
               return null;
            }
            if(intType.isSigned()) {
                v = ValueUtility.getSint64Value(l);
            } else {
                v = ValueUtility.getUint64Value(l);
            }
       } else if(type instanceof FloatDataType) {
            Double d = ((FloatDataType)type).getInitialValue();
            if(d==null) {
                return null;
            }
            v = ValueUtility.getDoubleValue(d);
        } else if(type instanceof StringDataType) {
            String sv = ((StringDataType)type).getInitialValue();
            if(sv==null) {
               return null;
            }
            v = ValueUtility.getStringValue(sv);
        } else if (type instanceof BinaryDataType) {
            byte[] b = ((BinaryDataType)type).getInitialValue();
            if(b==null) {
                return null;
            }
            v = ValueUtility.getBinaryValue(b);
        } else if (type instanceof EnumeratedDataType) {
            EnumeratedDataType enumType = (EnumeratedDataType)type;
            String sv = enumType.getInitialValue();
            if(sv==null) {
                return null;
            }
            v = ValueUtility.getStringValue(sv);
        } else if (type instanceof BooleanDataType) {
            Boolean b = ((BooleanDataType)type).getInitialValue();
            if(b==null) {
               return null;
            }
            v = ValueUtility.getBooleanValue(b);
        } else if (type instanceof AbsoluteTimeDataType) {
            Long t = ((AbsoluteTimeDataType)type).getInitialValue();
            if(t==null) {
               return null;
            }
            v = ValueUtility.getTimestampValue(t);
        }  else if (type instanceof AggregateDataType) {
            v =  getAggregateInitialValue((AggregateDataType) type);
        }  else if (type instanceof ArrayDataType) {
            return null; //TODO
        }  else {
            throw new IllegalArgumentException("Cannot parse values of type "+type);
        }
        return v;
    }

    private static Value getAggregateInitialValue(AggregateDataType aggregateDataType) {
        AggregateValue v = new AggregateValue(aggregateDataType.getMemberNames());
        for(Member m: aggregateDataType.getMemberList()) {
            Value mv = getInitialValue(m.getType());
            if(mv != null) {
                v.setValue(m.getName(), mv);
            }
        }
        if(v.numMembers() > 0) {
            return v;
        } else {
            return null;
        }
    }
    

  

    
}
