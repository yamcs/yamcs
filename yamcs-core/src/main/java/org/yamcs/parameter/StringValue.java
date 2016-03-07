package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

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
    
}
