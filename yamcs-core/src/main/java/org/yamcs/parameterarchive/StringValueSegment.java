package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;



public class StringValueSegment extends ObjectSegment<String> implements ValueSegment {
    static StringSerializer serializer = new StringSerializer();
    StringValueSegment(boolean buildForSerialisation) {
        super(serializer, buildForSerialisation);
    }


    public static final int MAX_UTF8_CHAR_LENGTH = 3; //I've seen this in protobuf somwhere
    protected List<String> values;


    
    public StringValueSegment consolidate() {
        return (StringValueSegment)super.consolidate();
    }

    @Override
    public Value getValue(int index) {
        return ValueUtility.getStringValue(get(index));
    }


    public void addValue(Value v) {
        add(v.getStringValue());
    }

    public static BaseSegment consolidate(List<Value> values) {
        StringValueSegment svs = new StringValueSegment(true);
        for(Value v: values) {
            svs.add(v.getStringValue());
        }
        return svs.consolidate();
    }

    @Override
    public void add(int pos, Value v) {
        add(pos, v.getStringValue());
    }

    public static StringValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        StringValueSegment r = new StringValueSegment(false);
        r.parse(bb);
        return r;
    }
    
    static class StringSerializer implements ObjectSerializer<String>  {
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

}
