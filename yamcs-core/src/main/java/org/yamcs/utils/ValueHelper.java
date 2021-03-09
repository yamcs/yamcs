package org.yamcs.utils;

import org.yamcs.protobuf.Yamcs.AggregateValue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.ByteString;

public class ValueHelper {
    /**
     * returns a SINT32 Value
     * @param x
     * @return
     */
    static public Value newValue(int x) {
        return Value.newBuilder().setType(Type.SINT32).setSint32Value(x).build();
    }

    static public Value newUnsignedValue(int x) {
        return Value.newBuilder().setType(Type.UINT32).setUint32Value(x).build();
    }

    /**
     * returns a DOUBLE Value
     * @param x
     * @return
     */
    static public Value newValue(double x) {
        return Value.newBuilder().setType(Type.DOUBLE).setDoubleValue(x).build();
    }

    /**
     * returns a FLOAT Value
     * @param x
     * @return
     */
    static public Value newValue(float x) {
        return Value.newBuilder().setType(Type.FLOAT).setFloatValue(x).build();
    }

    /**
     * returns a STRING Value
     * @param x
     * @return
     */
    static public Value newValue(String x) {
        return Value.newBuilder().setType(Type.STRING).setStringValue(x).build();
    }

    /**
     * returns a BINARY Value
     * @param x
     * @return
     */
    static public Value newValue(byte[] x) {
        return Value.newBuilder().setType(Type.BINARY).setBinaryValue(ByteString.copyFrom(x)).build();
    }

    /**
     * returns a BOOLEAN Value
     * @param x
     * @return
     */
    static public Value newValue(boolean x) {
        return Value.newBuilder().setType(Type.BOOLEAN).setBooleanValue(x).build();
    }

    /**
     * returns a TIMESTAMP Value
     * @param x
     * @return
     */
    public static Value newTimestampValue(long  x) {
        return Value.newBuilder().setType(Type.TIMESTAMP).setTimestampValue(x).build();
    }

    /**
     * returns new array value
     * @return
     */
    public static Value newArrayValue(Value...values) {
        Value.Builder vb = Value.newBuilder();
        vb.setType(Type.ARRAY);
        if(values.length==0) {
            return vb.build();
        }
        Value v0 = values[0];
        for(Value v: values) {
            if(v.getType()!=v0.getType()) {
                throw new IllegalArgumentException("Element arrays have to be all of the same type");
            }
            vb.addArrayValue(v);
        }
        return vb.build();
    }
    
    /**
     * returns new aggregate value
     * The passed arguments have to be of type String, Value, String Value...
     * @return
     */
    public static Value newAggregateValue(Object...objs) {
        if(objs.length%2!=0) {
            throw new IllegalArgumentException("This function requires an even number of arguments (String, Value)*");
        }
        AggregateValue.Builder agb = AggregateValue.newBuilder();
        
        for(int i =0; i<objs.length; i+=2) {
            if(!(objs[i] instanceof String)) {
                throw new IllegalArgumentException("Invalid type "+objs[i].getClass()+" of argument "+i+" should be String");
            }
            String name = (String)objs[i];
        
            if(!(objs[i+1] instanceof Value)) {
                throw new IllegalArgumentException("Invalid type "+objs[i+1].getClass()+" of argument "+(i+1)+" should be Value");
            }
            Value value = (Value)objs[i+1];
            agb.addName(name);
            agb.addValue(value);
        }
        
        return Value.newBuilder().setType(Type.AGGREGATE).setAggregateValue(agb.build()).build();
    }
}
