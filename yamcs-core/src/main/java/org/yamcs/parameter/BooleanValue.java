package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

public class BooleanValue extends Value {
    final boolean v;
    public static final BooleanValue TRUE = new BooleanValue(true);
    public static final BooleanValue FALSE = new BooleanValue(false);
    
    public BooleanValue(boolean v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.BOOLEAN;
    }
    
    @Override
    public boolean getBooleanValue() {
        return v;
    }
    
    @Override
    public int hashCode() {
        return Boolean.hashCode(v);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BooleanValue) {
            return v == ((BooleanValue)obj).v;
        }
        return false;
    }
    
    
    public String toString() {
        return Boolean.toString(v);
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.BOOLEANVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.BOOLEAN_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeBoolSize(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeBool(FIELD_NUM, v);
    }
}
