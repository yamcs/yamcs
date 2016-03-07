package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class FloatValue extends Value {
   final float v;
    
    public FloatValue(float v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.FLOAT;
    }
    
    @Override
    public float getFloatValue() {
        return v;
    }
    
    @Override
    public int hashCode() {
        return Float.hashCode(v);
    }
    
    public boolean equals(Object obj) {
        return (obj instanceof FloatValue)
               && (Float.floatToIntBits(((FloatValue)obj).v) == Float.floatToIntBits(v));
    }
    
    @Override
    public String toString() {
        return Float.toString(v);
    }
}
