package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

public class FloatValue extends Value {
    final float v;

    public FloatValue(float v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.FLOAT;
    }

    @Override
    public float getFloatValue() {
        return v;
    }

    @Override
    public double toDouble() {
        return v;
    }

    @Override
    public int hashCode() {
        return Float.hashCode(v);
    }

    public boolean equals(Object obj) {
        return (obj instanceof FloatValue)
                && (Float.floatToIntBits(((FloatValue) obj).v) == Float.floatToIntBits(v));
    }

    @Override
    public String toString() {
        return Float.toString(v);
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.FLOATVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.FLOAT_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream
            .computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream
                .computeFloatSize(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeFloat(FIELD_NUM, v);
    }
}
