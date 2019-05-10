package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * An enumerated value is a value that has both an integer and a string representation.
 * 
 * @author nm
 *
 */
public class EnumeratedValue extends Value {
    final String stringValue;
    final long longValue;

    public EnumeratedValue(long longValue, String stringValue) {
        this.longValue = longValue;
        this.stringValue = stringValue;
    }

    @Override
    public Type getType() {
        return Type.ENUMERATED;
    }

    @Override
    public long getSint64Value() {
        return longValue;
    }

    @Override
    public String getStringValue() {
        return stringValue;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(longValue) ^ stringValue.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof EnumeratedValue) {
            return ((longValue == ((EnumeratedValue) obj).longValue)
                    && stringValue.equals(((EnumeratedValue) obj).stringValue));
        }
        return false;
    }
    
    @Override
    public String toString() {
        return stringValue;
    }
}
