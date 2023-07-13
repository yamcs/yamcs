package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;

public class TimestampValue extends Value {
    final long millis;
    final int nanos;
    
    public TimestampValue(long millis) {
        this(millis, 0);
    }

    public TimestampValue(long millis, int nanos) {
        this.millis = millis;
        this.nanos = nanos;
    }
    @Override
    public Type getType() {
        return Type.TIMESTAMP;
    }
    
    @Override
    public long getTimestampValue() {
        return millis;
    }

    public long millis() {
        return millis;
    }
    
    public long nanos() {
        return nanos;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(millis);
    }
 
    public boolean equals(Object obj) {
        if (obj instanceof TimestampValue) {
            return millis == ((TimestampValue) obj).millis;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return TimeEncoding.toString(millis);
    }
}
