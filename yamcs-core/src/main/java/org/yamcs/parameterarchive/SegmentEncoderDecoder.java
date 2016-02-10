package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;

import org.yamcs.utils.DecodingException;

public class SegmentEncoderDecoder {
    
    public byte[] encode(BaseSegment valueSegment) {
        ByteBuffer bb = ByteBuffer.allocate(2+valueSegment.getMaxSerializedSize());
        bb.put(valueSegment.getFormatId());
        valueSegment.writeTo(bb);
        if(bb.position()<bb.capacity()) {
            int length = bb.position();
            byte[] v = new byte[length];
            bb.rewind();
            bb.get(v, 0, length);
            return v;
        } else {
            return bb.array();
        }
    }
    
    
    public BaseSegment decode(byte[] buf, long segmentStart) throws DecodingException {
        ByteBuffer bb = ByteBuffer.wrap(buf);
     
        byte formatId = bb.get();
        
        BaseSegment vs = BaseSegment.parseSegment(formatId, segmentStart, bb);
        return vs;
    }
}
