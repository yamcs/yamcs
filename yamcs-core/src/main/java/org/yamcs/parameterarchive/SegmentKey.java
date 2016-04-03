package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;

import org.yamcs.utils.StringConverter;

/**
 * Holder, encoder and decoder for the segment keys (in the sense of key,value storage used for RocksDb)
 *   
 * @author nm
 *
 */
class SegmentKey {
  

    final int parameterId;
    final int parameterGroupId;
    final long segmentStart;
    byte type;
    public static final byte TYPE_ENG_VALUE = 0;
    public static final byte TYPE_RAW_VALUE = 1;
    public static final byte TYPE_PARAMETER_STATUS = 2;
    
    public SegmentKey(int parameterId, int parameterGroupId, long segmentStart, byte type) {
        this.parameterId = parameterId;
        this.parameterGroupId = parameterGroupId;
        this.segmentStart = segmentStart;
        this.type = type;
    }
    

    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(17);
        bb.putInt(parameterId);
        bb.putInt(parameterGroupId);
        bb.putLong(segmentStart);
        bb.put(type);
        return bb.array();
    }

    public static SegmentKey decode(byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b);
        int parameterId = bb.getInt();
        int parameterGroupId = bb.getInt();
        long segmentStart = bb.getLong();
        byte type = bb.get();
        return new SegmentKey(parameterId, parameterGroupId, segmentStart, type);
    }
    
    @Override
    public String toString() {
        return "SegmentKey [parameterId=" + parameterId + ", parameterGroupId="
                + parameterGroupId + ", segmentStart=" + segmentStart
                + ", type=" + type + " encoded: "+StringConverter.arrayToHexString(encode())+"]";
    }
}