package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

public class DoubleValue extends Value {
    final double v;

    public DoubleValue(double v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.DOUBLE;
    }

    @Override
    public double getDoubleValue() {
        return v;
    }

    @Override
    public double toDouble() {
        return v;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(v);
    }

    public boolean equals(Object obj) {
        return (obj instanceof DoubleValue)
                && (Double.doubleToLongBits(((DoubleValue) obj).v) == Double.doubleToLongBits(v));
    }

    @Override
    public String toString() {
        return Double.toString(v);
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.DOUBLEVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.DOUBLE_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeDoubleSize(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeDouble(FIELD_NUM, v);
    }

}
