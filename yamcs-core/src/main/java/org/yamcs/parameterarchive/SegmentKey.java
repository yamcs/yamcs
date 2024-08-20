package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;

import org.yamcs.utils.StringConverter;

/**
 * Holder, encoder and decoder for the segment keys (in the sense of key,value storage used for RocksDb)
 *
 */
public class SegmentKey {
    final int parameterId;
    final int parameterGroupId;
    final long segmentStart;
    byte type;
    public static final byte TYPE_ENG_VALUE = 0;
    public static final byte TYPE_RAW_VALUE = 1;
    public static final byte TYPE_PARAMETER_STATUS = 2;
    public static final byte TYPE_GAPS = 3;

    public SegmentKey(int parameterId, int parameterGroupId, long segmentStart, byte type) {
        this.parameterId = parameterId;
        this.parameterGroupId = parameterGroupId;
        this.segmentStart = segmentStart;
        this.type = type;
    }

    /**
     * Key encode in Yamcs starting with 5.10 - we use the invertSign for the timestamps in order for the negative times
     * to sort before the positive ones in the archive
     */
    public byte[] encode() {
        return encode(parameterId, parameterGroupId, segmentStart, type);
    }

    /**
     * Key decode in Yamcs starting with 5.10 - we use the invertSign for the timestamps in order for the negative times
     * to sort before the positive ones in the archive
     */
    public static SegmentKey decode(byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b);
        int parameterId = bb.getInt();
        int parameterGroupId = bb.getInt();
        long segmentStart = invertSign(bb.getLong());
        byte type = bb.get();
        return new SegmentKey(parameterId, parameterGroupId, segmentStart, type);
    }

    /**
     * Key encode in Yamcs starting with 5.10 - we use the invertSign for the timestamps in order for the negative times
     * to sort before the positive ones in the archive
     */
    public static byte[] encode(int parameterId, int parameterGroupId, long segmentStart, byte type) {
        ByteBuffer bb = ByteBuffer.allocate(17);
        bb.putInt(parameterId);
        bb.putInt(parameterGroupId);
        bb.putLong(invertSign(segmentStart));
        bb.put(type);
        return bb.array();
    }

    /**
     * Version 0 is prior to Yamcs 5.10 when the negative times were not sorting properly
     * 
     */
    public byte[] encodeV0() {
        return encodeV0(parameterId, parameterGroupId, segmentStart, type);
    }

    /**
     * Version 0 is prior to Yamcs 5.10 when the negative times were not sorting properly
     * 
     */
    public static SegmentKey decodeV0(byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b);
        int parameterId = bb.getInt();
        int parameterGroupId = bb.getInt();
        long segmentStart = bb.getLong();
        byte type = bb.get();
        return new SegmentKey(parameterId, parameterGroupId, segmentStart, type);
    }

    /**
     * Version 0 is prior to Yamcs 5.10 when the negative times were not sorting properly
     * 
     */
    public static byte[] encodeV0(int parameterId, int parameterGroupId, long segmentStart, byte type) {
        ByteBuffer bb = ByteBuffer.allocate(17);
        bb.putInt(parameterId);
        bb.putInt(parameterGroupId);
        bb.putLong(segmentStart);
        bb.put(type);
        return bb.array();
    }

    /**
     * inverting the sign causes negative numbers to be sorted before the positive ones when converted to binary
     */
    static long invertSign(long x) {
        return x ^ Long.MIN_VALUE;
    }

    @Override
    public String toString() {
        return "SegmentKey [parameterId=" + parameterId + ", parameterGroupId="
                + parameterGroupId + ", segmentStart=" + segmentStart
                + ", type=" + type + " encoded: " + StringConverter.arrayToHexString(encode()) + "]";
    }
}
