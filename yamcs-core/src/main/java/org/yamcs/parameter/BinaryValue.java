package org.yamcs.parameter;

import java.io.IOException;
import java.util.Arrays;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;

import com.google.protobuf.CodedOutputStream;

public class BinaryValue extends Value {
    final  byte[] v;
    
    public BinaryValue(byte[] v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.BINARY;
    }
    
    @Override
    public byte[] getBinaryValue() {
        return v;
    }
    
    
    @Override
    public int hashCode() {
        return Arrays.hashCode(v);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        
        if(obj instanceof BinaryValue) {
            return Arrays.equals(v, ((BinaryValue)obj).v);
        }
        
        return false;
    }
    
    @Override
    public String toString() {
        return StringConverter.arrayToHexString(v);
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.BINARYVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.BINARY_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeByteArraySize(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeByteArray(FIELD_NUM, v);
    }
}
