package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.CodedOutputStream;

public class TimestampValue extends Value {
   final long v;
    
    public TimestampValue(long v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.TIMESTAMP;
    }
    
    @Override
    public long getTimestampValue() {
        return v;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(v);
    }
 
    public boolean equals(Object obj) {
        if (obj instanceof TimestampValue) {
            return v == ((TimestampValue)obj).v;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return TimeEncoding.toString(v);
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.TIMESTAMPVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.TIMESTAMP_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeSInt64Size(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeSInt64(FIELD_NUM, v);
    }
}
