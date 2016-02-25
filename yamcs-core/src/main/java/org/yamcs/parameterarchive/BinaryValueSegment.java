package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.List;

import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;


public class BinaryValueSegment extends ObjectSegment<byte[]> implements ValueSegment {  
    static BinarySerializer serializer = new BinarySerializer();
    
    BinaryValueSegment(boolean buildForSerialisation) {
        super(serializer, buildForSerialisation);
    }


    public static final int MAX_UTF8_CHAR_LENGTH = 3; //I've seen this in protobuf somwhere
    protected List<String> values;
    

    
    @Override
    public Value getValue(int index) {
        return ValueUtility.getBinaryValue(get(index));
    }

    public BinaryValueSegment consolidate() {
        return (BinaryValueSegment) super.consolidate();
    }

    @Override
    public void add(int pos, Value v) {
       add(pos, v.getBinaryValue());
    }
    
    public static BinaryValueSegment consolidate(List<Value> values) {
        BinaryValueSegment bvs = new BinaryValueSegment(true);
        for(Value v: values) {
            bvs.add(v.getBinaryValue());
        }
        return bvs.consolidate();
    }

    public static BinaryValueSegment parseFrom(ByteBuffer bb) throws DecodingException {
        BinaryValueSegment r = new BinaryValueSegment(false);
        r.parse(bb);
        return r;
    }
    
    static class BinarySerializer implements ObjectSerializer<byte[]>  {
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



}
