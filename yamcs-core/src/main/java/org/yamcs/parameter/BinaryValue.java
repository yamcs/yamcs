package org.yamcs.parameter;

import java.util.Arrays;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;

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
}
