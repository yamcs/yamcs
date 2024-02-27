package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.protobuf.Yamcs.Value.Type;

import com.google.protobuf.CodedOutputStream;

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

    /**** Protobuf methods **/
    static final int SINT64_FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.SINT64VALUE_FIELD_NUMBER;
    static final int STRING_FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.STRINGVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.ENUMERATED_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);

    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeSInt64Size(SINT64_FIELD_NUM, longValue)
                + com.google.protobuf.CodedOutputStream.computeStringSize(STRING_FIELD_NUM, stringValue);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);
        output.writeSInt64(SINT64_FIELD_NUM, longValue);
        output.writeString(SINT64_FIELD_NUM, stringValue);
    }
}
