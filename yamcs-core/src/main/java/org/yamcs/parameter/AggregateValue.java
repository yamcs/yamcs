package org.yamcs.parameter;

import java.io.IOException;

import org.yamcs.http.YamcsEncoded;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.util.AggregateMemberNames;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

public class AggregateValue extends Value {
    AggregateMemberNames names;
    Value[] values;

    /**
     * Create a new aggregate value with the member names.
     * Make sure that the memberNames are interned string (see {@link String#intern()},
     * for example as returned by {@link AggregateDataType#getMemberNames()}
     * 
     * 
     * @param memberNames
     */
    public AggregateValue(AggregateMemberNames memberNames) {
        this.names = memberNames;
        this.values = new Value[memberNames.size()];
    }

    private int idx(String name) {
        int idx = names.indexOf(name);
        if (idx == -1) {
            throw new IllegalArgumentException("No member named '" + name + "'");
        }
        return idx;
    }

    public void setMemberValue(String name, Value value) {
        setMemberValue(idx(name), value);
    }

    /**
     * Returns the value of the member with the given name
     * 
     * @param name
     *            the name of the aggregate member whos value has to be returned
     * 
     * @return the value of the member with the given name
     * @throws IllegalArgumentException
     *             if there is no member with that name
     */
    public Value getMemberValue(String name) {
        return values[idx(name)];
    }

    public void setMemberValue(int idx, Value value) {
        if (value == null) {
            throw new NullPointerException();
        }
        values[idx] = value;
    }

    /**
     * Get the index of the member with the given name or -1 if there is no such member
     * 
     * @param name
     * @return
     */
    public int getMemberIndex(String name) {
        return names.indexOf(name);
    }

    @Override
    public Type getType() {
        return Type.AGGREGATE;
    }

    public int numMembers() {
        return values.length;
    }

    public String getMemberName(int idx) {
        return names.get(idx);
    }

    public Value getMemberValue(int i) {
        return values[i];
    }

    public AggregateMemberNames getMemberNames() {
        return names;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < values.length; i++) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(names.get(i)).append(" : ").append(values[i]);
        }
        return sb.toString();
    }

    /**** Protobuf methods **/
    static final int FIELD_NUM = org.yamcs.protobuf.Yamcs.Value.AGGREGATEVALUE_FIELD_NUMBER;
    static final int TYPE = org.yamcs.protobuf.Yamcs.Value.Type.AGGREGATE_VALUE;
    static final int TYPE_SIZE = com.google.protobuf.CodedOutputStream.computeEnumSize(1, TYPE);



    @Override
    public int getSerializedSize() {
        return TYPE_SIZE + com.google.protobuf.CodedOutputStream.computeTagSize(FIELD_NUM)
                + YamcsEncoded.computeLengthDelimitedFieldSize(getSerializedDataSize());
    }

    int memoizedDataSize = -1;

    private int getSerializedDataSize() {
        int dataSize = memoizedDataSize;

        if (dataSize == -1) {
            dataSize = 0;
            for (int i = 0; i < values.length; i++) {
                dataSize += com.google.protobuf.CodedOutputStream
                        .computeStringSize(org.yamcs.protobuf.Yamcs.AggregateValue.NAME_FIELD_NUMBER, names.get(i));

                dataSize += YamcsEncoded.computeMessageSize(org.yamcs.protobuf.Yamcs.AggregateValue.VALUE_FIELD_NUMBER,
                        values[i]);
            }
            memoizedDataSize = dataSize;

        }
        return dataSize;
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.writeEnum(1, TYPE);

        int dataSize = getSerializedDataSize();
        output.writeTag(FIELD_NUM, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(dataSize);

        for (int i = 0; i < values.length; i++) {
            output.writeString(org.yamcs.protobuf.Yamcs.AggregateValue.NAME_FIELD_NUMBER, names.get(i));

            output.writeTag(org.yamcs.protobuf.Yamcs.AggregateValue.VALUE_FIELD_NUMBER,
                    WireFormat.WIRETYPE_LENGTH_DELIMITED);
            output.writeUInt32NoTag(values[i].getSerializedSize());
            values[i].writeTo(output);
        }
    }
}
