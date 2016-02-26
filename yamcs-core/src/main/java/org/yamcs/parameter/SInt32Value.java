package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class SInt32Value extends Value {
   final  int v;
    
    public SInt32Value(int v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.SINT32;
    }
    
    @Override
    public int getSint32Value() {
        return v;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(v);
    }
    
    public boolean equals(Object obj) {
        if (obj instanceof SInt32Value) {
            return v == ((SInt32Value)obj).v;
        }
        return false;
    }

    @Override
    public String toString() {
        return Integer.toString(v);
    }
}
