package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

public class StringValue extends Value {
   final  String v;
    
    public StringValue(String v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.STRING;
    }
    
    @Override
    public String getStringValue() {
        return v;
    }
    
    @Override
    public int hashCode() {
        return v.hashCode();
    }
    
    public boolean equals(Object obj) {
        if (obj instanceof StringValue) {
            return v.equals(((StringValue)obj).v);
        }
        return false;
    }
    
    @Override
    public String toString() {
        return v;
    }
    
    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.STRINGVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.STRING_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeStringSize(FIELD_NUM, v);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeString(FIELD_NUM, v);
    }

}
