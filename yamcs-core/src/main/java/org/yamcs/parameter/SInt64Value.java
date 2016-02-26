package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class SInt64Value extends Value {
   final long v;
    
    public SInt64Value(long v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.SINT64;
    }
    
    @Override
    public long getSint64Value() {
        return v;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(v);
    }
    
    public boolean equals(Object obj) {
        if (obj instanceof SInt64Value) {
            return v == ((SInt64Value)obj).v;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return Long.toString(v);
    }
}
