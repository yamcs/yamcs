package org.yamcs.parameter;

import java.util.Objects;
import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.TimeEncoding;
import com.google.protobuf.CodedOutputStream;

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

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.TIMESTAMPVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.TIMESTAMP_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeSInt64Size(FIELD_NUM, millis);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeSInt64(FIELD_NUM, millis);
    }
}
