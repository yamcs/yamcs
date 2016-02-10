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
        return Value.newBuilder().setType(Value.Type.SINT32).setSint32Value(x).build(); 
    }

    public static Value getUint64Value(long x) {
        return Value.newBuilder().setType(Value.Type.UINT64).setUint64Value(x).build(); 
    }

    public static Value getSint64Value(long x) {
        return Value.newBuilder().setType(Value.Type.SINT64).setSint64Value(x).build(); 
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
    

    public static Value getBooleanValue(boolean b) {
        return Value.newBuilder().setType(Value.Type.BOOLEAN).setBooleanValue(b).build(); 
    }
    

    public static Value getFloatValue(float f) {
        return Value.newBuilder().setType(Value.Type.FLOAT).setFloatValue(f).build();
    }
    
    public static Value getDoubleValue(double d) {
        return Value.newBuilder().setType(Value.Type.DOUBLE).setDoubleValue(d).build();
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
        case BOOLEAN:
            return v.getBooleanValue();
        case SINT64:
            return v.getSint64Value();
        case UINT64:
            return v.getSint64Value();
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
    
    public static boolean equals(Value a, Value b) {
        if (a == null ^ b == null)
            return false;
        if (a == null && b == null)
            return true;
        if (a.getType() != b.getType())
            return false;
        
        switch (a.getType()) {
        case BINARY:
            return a.getBinaryValue().equals(b.getBinaryValue());
        case BOOLEAN:
            return a.getBooleanValue() == b.getBooleanValue();
        case DOUBLE:
            return a.getDoubleValue() == b.getDoubleValue();
        case FLOAT:
            return a.getFloatValue() == b.getFloatValue();
        case SINT32:
            return a.getSint32Value() == b.getSint32Value();
        case SINT64:
            return a.getSint64Value() == b.getSint64Value();
        case STRING:
            return a.getStringValue().equals(b.getStringValue());
        case TIMESTAMP:
            return a.getTimestampValue() == b.getTimestampValue();
        case UINT32:
            return a.getUint32Value() == b.getUint32Value();
        case UINT64:
            return a.getUint64Value() == b.getUint64Value();
        default:
            throw new IllegalStateException("Unexpected type " + a.getType());
        }
    }
    
    // Not perfect. Should also compare compatible types
    public static int compare(Value a, Value b) {
        if (a == null ^ b == null)
            return (a == null) ? -1 : 1;
        if (a == null && b == null)
            return 0;
        if (a.getType() != b.getType())
            return a.getType().compareTo(b.getType());
        
        switch (a.getType()) {
        case BINARY:
            return String.valueOf(a).compareTo(String.valueOf(b)); // TODO ?
        case BOOLEAN:
            return Boolean.compare(a.getBooleanValue(), b.getBooleanValue());
        case DOUBLE:
            return Double.compare(a.getDoubleValue(), b.getDoubleValue());
        case FLOAT:
            return Float.compare(a.getFloatValue(), b.getFloatValue());
        case SINT32:
            return Integer.compare(a.getSint32Value(), b.getSint32Value());
        case SINT64:
            return Long.compare(a.getSint64Value(), b.getSint64Value());
        case STRING:
            return a.getStringValue().compareTo(b.getStringValue());
        case TIMESTAMP:
            return Long.compare(a.getTimestampValue(), b.getTimestampValue());
        case UINT32:
            return Integer.compareUnsigned(a.getUint32Value(), b.getUint32Value());
        case UINT64:
            return Long.compareUnsigned(a.getUint64Value(), b.getUint64Value());
        default:
            throw new IllegalStateException("Unexpected type " + a.getType());
        }
    }
}
