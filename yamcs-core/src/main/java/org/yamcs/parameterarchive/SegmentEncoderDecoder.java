package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.yamcs.utils.DecodingException;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.VarIntUtil;

import static org.yamcs.parameterarchive.BaseSegment.*;

public class SegmentEncoderDecoder {

    static public byte[] encode(BaseSegment valueSegment) {
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

    static public BaseSegment decode(byte[] buf, long segmentStart) throws DecodingException {
        buf = Arrays.copyOf(buf, buf.length + 16);
        ByteBuffer bb = ByteBuffer.wrap(buf);
        byte formatId = bb.get();
        return BaseSegment.parseSegment(formatId, segmentStart, bb);
    }

    /**
     * the gaps is a sorted int array so we encode it with the same encoding like the time segment
     */
    static public byte[] encodeGaps(int segStartIdxInsideInterval, SortedIntArray gaps) {
        ByteBuffer bb = ByteBuffer.allocate(5 + 4 * gaps.size());
        bb.put(FORMAT_ID_GapSegment);
        VarIntUtil.writeVarInt32(bb, segStartIdxInsideInterval);
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

    static public SortedIntArray decodeGaps(byte[] buf) throws DecodingException {
        ByteBuffer bb = ByteBuffer.wrap(buf);

        byte formatId = bb.get();
        if (formatId == FORMAT_ID_SortedTimeValueSegmentV1) {
            return SortedTimeSegment.parse(bb);
        } else if (formatId == FORMAT_ID_GapSegment) {
            /* int segStartIdxInsideInterval =*/ VarIntUtil.readVarInt32(bb);
            SortedIntArray a = SortedTimeSegment.parse(bb);
            return a;
        } else {
            throw new DecodingException("Invalid format id " + formatId + " for gaps");
        }
    }

    // makes a gap segment with all gaps between idx1 (inclusive) and idx2 (exclusive)
    public static byte[] encodeGaps(int idx1, int idx2) {
        int[] x = new int[idx2 - idx1];
        for (int i = 0; i < x.length; i++) {
            x[i] = i;
        }
        SortedIntArray gaps = new SortedIntArray(x);
        return encodeGaps(idx1, gaps);
    }

}
