package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class BooleanValue extends Value {
   final boolean v;
    
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
}
