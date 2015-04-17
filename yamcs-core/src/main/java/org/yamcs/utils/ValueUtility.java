package org.yamcs.utils;

import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;

import com.google.protobuf.ByteString;

public class ValueUtility {
    public static Value getUint32Value(int x) {
        return Value.newBuilder().setType(Value.Type.UINT32).setUint32Value(x).build(); 
    }
    
    public static Value getSint32Value(int x) {
        return Value.newBuilder().setType(Value.Type.SINT32).setUint32Value(x).build(); 
    }
    
    public static Value getStringValue(String x) {
        return Value.newBuilder().setType(Value.Type.STRING).setStringValue(x).build(); 
    }
    
    public static Value getBinaryValue(byte[] x) {
        return Value.newBuilder().setType(Value.Type.BINARY).setBinaryValue(ByteString.copyFrom(x)).build(); 
    }
    
    public static Value getTimestampValue(long x) {
        return Value.newBuilder().setType(Value.Type.TIMESTAMP).setTimestampValue(x).build(); 
    }
    
    public static Value getColumnValue(ColumnDefinition cd, Object v) {
        switch (cd.getType().val) { //TODO all types
        case INT:
            return getSint32Value((Integer)v);
        case STRING:
            return getStringValue((String)v);
        case TIMESTAMP:
            return ValueHelper.newTimestampValue((Long)v);
        case BINARY:
            return getBinaryValue((byte[])v);
        case BOOLEAN:
        	return ValueHelper.newValue((Boolean)v);
        }
        throw new RuntimeException("cannot convert type to value "+cd.getType());
    }

    
	public static Object getYarchValue(Value v) {
        switch(v.getType()) {
        case BINARY:
            return v.getBinaryValue().toByteArray();
        case SINT32:
            return v.getSint32Value();
        case UINT32:
            return v.getUint32Value();
        case DOUBLE:
            return v.getDoubleValue();
        case FLOAT:
            return (double)v.getFloatValue();
        case STRING:
            return v.getStringValue();
        case TIMESTAMP:
            return v.getTimestampValue();
        }
        throw new RuntimeException("cannot values of type "+v.getType());
    }
    public static DataType getYarchType(Value v) {
        switch(v.getType()) {
        case BINARY:
            return DataType.BINARY;
        case SINT32:
            return DataType.INT;
        case UINT32:
            return DataType.INT;
        case DOUBLE:
            return DataType.DOUBLE;
        case FLOAT:
            return DataType.DOUBLE;
        case STRING:
            return DataType.STRING;
        case TIMESTAMP:
            return DataType.TIMESTAMP;
        }
        throw new RuntimeException("cannot values of type "+v.getType());
    }
}
