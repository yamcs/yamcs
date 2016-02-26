package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

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
    public int hashCode() {
        return Integer.hashCode(v);
    }
 
    public boolean equals(Object obj) {
        if (obj instanceof UInt32Value) {
            return v == ((UInt32Value)obj).v;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return Integer.toUnsignedString(v);
    }
}
