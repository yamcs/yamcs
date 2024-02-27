package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

public class SInt32Value extends Value {
    final int v;

    public SInt32Value(int v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.SINT32;
    }

    @Override
    public int getSint32Value() {
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
        return Integer.hashCode(v);
    }

    public boolean equals(Object obj) {
        if (obj instanceof SInt32Value) {
            return v == ((SInt32Value) obj).v;
        }
        return false;
    }

    @Override
    public String toString() {
        return Integer.toString(v);
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.SINT32VALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.SINT32_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeSInt32Size(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeSInt32(FIELD_NUM, v);
    }
}
