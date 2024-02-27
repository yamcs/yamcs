package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

public class SInt64Value extends Value {
    final long v;

    public SInt64Value(long v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.SINT64;
    }

    @Override
    public long getSint64Value() {
        return v;
    }

    @Override
    public long toLong() {
        return v;
    }

    @Override
    public double toDouble() {
        return v;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(v);
    }

    public boolean equals(Object obj) {
        if (obj instanceof SInt64Value) {
            return v == ((SInt64Value) obj).v;
        }
        return false;
    }

    @Override
    public String toString() {
        return Long.toString(v);
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.SINT64VALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.SINT64_VALUE;
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
