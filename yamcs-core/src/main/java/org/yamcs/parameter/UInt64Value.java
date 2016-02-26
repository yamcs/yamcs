package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

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
    public int hashCode() {
        return Long.hashCode(v);
    }
    
    public boolean equals(Object obj) {
        if (obj instanceof UInt64Value) {
            return v == ((UInt64Value)obj).v;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return Long.toUnsignedString(v);
    }
}
