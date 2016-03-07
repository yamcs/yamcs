package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;

public class TimestampValue extends Value {
   final long v;
    
    public TimestampValue(long v) {
        this.v = v;
    }

    @Override
    public Type getType() {
        return Type.TIMESTAMP;
    }
    
    @Override
    public long getTimestampValue() {
        return v;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(v);
    }
 
    public boolean equals(Object obj) {
        if (obj instanceof TimestampValue) {
            return v == ((TimestampValue)obj).v;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return TimeEncoding.toString(v);
    }
}
