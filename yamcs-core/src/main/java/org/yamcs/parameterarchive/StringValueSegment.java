package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;

public class StringValueSegment extends ObjectSegment<String> implements ValueSegment {
    static StringSerializer serializer = new StringSerializer();

    StringValueSegment(boolean buildForSerialisation) {
        super(serializer, buildForSerialisation);
    }

    public static final int MAX_UTF8_CHAR_LENGTH = 3; // I've seen this in protobuf somwhere

    @Override
    public void insert(int pos, Value value) {
        add(pos, value.getStringValue());
    }

    @Override
    public void add(Value value) {
        add(value.getStringValue());
    }

    @Override
    public Value getValue(int index) {
        return ValueUtility.getStringValue(get(index));
    }

    public void addValue(Value v) {
        add(v.getStringValue());
    }

    public static StringValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        StringValueSegment r = new StringValueSegment(false);
        r.parse(bb);
        return r;
    }

    static class StringSerializer implements ObjectSerializer<String> {
        @Override
        public byte getFormatId() {
            return BaseSegment.FORMAT_ID_StringValueSegment;
        }

        @Override
        public String deserialize(byte[] b) throws DecodingException {
            return new String(b, StandardCharsets.UTF_8);
        }

        @Override
        public byte[] serialize(String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public ValueArray getRange(int posStart, int posStop, boolean ascending) {
        return new ValueArray(getRangeArray(posStart, posStop, ascending));
    }

}
