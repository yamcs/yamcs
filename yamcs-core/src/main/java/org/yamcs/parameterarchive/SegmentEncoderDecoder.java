package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;

import org.yamcs.utils.DecodingException;
import org.yamcs.utils.SortedIntArray;

public class SegmentEncoderDecoder {
    public static final byte FORMAT_ID_GAPS_SortedIntArray = 1;

    public byte[] encode(BaseSegment valueSegment) {
        ByteBuffer bb = ByteBuffer.allocate(2 + valueSegment.getMaxSerializedSize());
        bb.put(valueSegment.getFormatId());
        valueSegment.writeTo(bb);
        if (bb.position() < bb.capacity()) {
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

        return BaseSegment.parseSegment(formatId, segmentStart, bb);
    }

    /**
     * the gaps is a sorted int array so we encode it with the same encoding like the time segment
     */
    public byte[] encodeGaps(SortedIntArray gaps) {
        ByteBuffer bb = ByteBuffer.allocate(5 + 4 * (gaps.size()));
        bb.put(FORMAT_ID_GAPS_SortedIntArray);
        SortedTimeSegment.writeTo(gaps, bb);

        if (bb.position() < bb.capacity()) {
            int length = bb.position();
            byte[] v = new byte[length];
            bb.rewind();
            bb.get(v, 0, length);
            return v;
        } else {
            return bb.array();
        }
    }


    public SortedIntArray decodeGaps(byte[] buf) throws DecodingException {
        ByteBuffer bb = ByteBuffer.wrap(buf);

        byte formatId = bb.get();
        if (formatId == FORMAT_ID_GAPS_SortedIntArray) {
            return SortedTimeSegment.parse(bb);
        } else {
            throw new DecodingException("Invalid format id " + formatId);
        }
    }

}
