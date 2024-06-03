package org.yamcs.parameter;

import java.util.Objects;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;

/**
 * Picosecond resolution timestamp stored as (millis, picos)
 */
public class TimestampValue extends Value {
    final long millis;
    final int picos;

    public TimestampValue(long millis) {
        this(millis, 0);
    }

    public TimestampValue(long millis, int picos) {
        this.millis = millis;
        this.picos = picos;
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

    public int picos() {
        return picos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(millis, picos);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TimestampValue other = (TimestampValue) obj;
        return millis == other.millis && picos == other.picos;
    }

    @Override
    public String toString() {
        return TimeEncoding.toString(millis);
    }
}
