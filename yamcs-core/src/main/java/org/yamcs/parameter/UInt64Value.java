package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

public class UInt64Value extends Value {
    final long v;

    public UInt64Value(long v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.UINT64;
    }

    @Override
    public long getUint64Value() {
        return v;
    }

    @Override
    public long toLong() {
        if (v < 0) {
            return v;
        } else {
            throw new UnsupportedOperationException(
                    "Cannot use convert value " + Long.toUnsignedString(v) + " to signed long");
        }
    }

    @Override
    public double toDouble() {
        return unsignedAsDouble(v);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(v);
    }

    public boolean equals(Object obj) {
        if (obj instanceof UInt64Value) {
            return v == ((UInt64Value) obj).v;
        }
        return false;
    }

    static public double unsignedAsDouble(long x) {
        double d = (double) x;
        if (d < 0) {
            d += 18446744073709551616.0;
        }
        return d;
    }

    @Override
    public String toString() {
        return Long.toUnsignedString(v);
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.UINT64VALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.UINT64_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeUInt64Size(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeUInt64(FIELD_NUM, v);
    }

}
