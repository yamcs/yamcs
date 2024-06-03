package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class BinaryValueSegment extends ObjectSegment<byte[]> implements ValueSegment {
    static BinarySerializer serializer = new BinarySerializer();

    BinaryValueSegment(boolean buildForSerialisation) {
        super(serializer, buildForSerialisation);
    }

    public static final int MAX_UTF8_CHAR_LENGTH = 3; // I've seen this in protobuf somwhere
    protected List<String> values;

    @Override
    public Value getValue(int index) {
        return ValueUtility.getBinaryValue(get(index));
    }

    @Override
    public void insert(int pos, Value value) {
        add(pos, value.getBinaryValue());
    }

    @Override
    public void add(Value v) {
        add(v.getBinaryValue());
    }

    public static BinaryValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        BinaryValueSegment r = new BinaryValueSegment(false);
        r.parse(bb);
        return r;
    }

    static class BinarySerializer implements ObjectSerializer<byte[]> {
        @Override
        public byte getFormatId() {
            return BaseSegment.FORMAT_ID_BinaryValueSegment;
        }

        @Override
        public byte[] deserialize(byte[] b) throws DecodingException {
            return b;
        }

        @Override
        public byte[] serialize(byte[] b) {
            return b;
        }
    }

    @Override
    public ValueArray getRange(int posStart, int posStop, boolean ascending) {
        return new ValueArray(getRangeArray(posStart, posStop, ascending));
    }
}
