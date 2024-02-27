package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

public class UInt32Value extends Value {
    final int v;

    public UInt32Value(int v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.UINT32;
    }

    @Override
    public int getUint32Value() {
        return v;
    }

    @Override
    public long toLong() {
        return v & 0xFFFFFFFFL;
    }

    @Override
    public double toDouble() {
        return toLong();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(v);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UInt32Value) {
            return v == ((UInt32Value) obj).v;
        }
        return false;
    }

    @Override
    public String toString() {
        return Integer.toUnsignedString(v);
    }


    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.UINT32VALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.UINT32_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeUInt32Size(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeUInt32(FIELD_NUM, v);
    }
}
