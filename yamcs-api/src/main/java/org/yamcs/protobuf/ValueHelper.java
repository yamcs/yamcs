package org.yamcs.protobuf;

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

}
