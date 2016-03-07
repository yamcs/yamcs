package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class DoubleValue extends Value {
   final double v;
    
    public DoubleValue(double v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.DOUBLE;
    }
    
    @Override
    public double getDoubleValue() {
        return v;
    }
    
    @Override
    public int hashCode() {
        return Double.hashCode(v);
    }
    
    public boolean equals(Object obj) {
        return (obj instanceof DoubleValue)
               && (Double.doubleToLongBits(((DoubleValue)obj).v) == Double.doubleToLongBits(v));
    }
    
    @Override
    public String toString() {
        return Double.toString(v);
    }
}
