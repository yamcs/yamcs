package org.yamcs.yarch;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.utils.ByteArrayUtils;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

/**
 * V2 histogram encoder:
 * <p>
 * num is stored on 32 bits supporting up to 4 billion records in a segment
 * <p>
 * timestamp is inverted before encoding, ensuring proper sorting where negative timestamps sort before positive ones in
 * chronological order.
 * <p>
 * segment size: 2^22 millisec ≈ 70 minutes
 */
public class HistogramEncoderV2 implements HistogramEncoder {
    private static final int RECORD_SIZE = 12; // 4 + 4 + 4 bytes
    private static final int SHIFT_AMOUNT = 22;
    private static final long MASK = (1L << 22) - 1;
    
    @Override
    public HistogramEncoder.SplitTimeResult splitTime(long timestamp) {
        long x = HistogramSegment.invertSignI64(timestamp);
        long segIndex = x >>> SHIFT_AMOUNT;
        int deltaTime = (int) (x & MASK);
        return new HistogramEncoder.SplitTimeResult(segIndex, deltaTime);
    }


    @Override
    public long segIndex(long timestamp) {
        long x = HistogramSegment.invertSignI64(timestamp);
        return x >>> SHIFT_AMOUNT;
    }
    
    @Override
    public long segmentStart(long timestamp) {
        return timestamp & (~MASK);
    }
    
    @Override
    public long joinTime(long segIndex, int deltaTime) {
        long x = (segIndex << SHIFT_AMOUNT) + deltaTime;
        return HistogramSegment.invertSignI64(x);
    }
    
    @Override
    public byte[] encodeKey(int tbsIndex, long segIndex, byte[] columnValue) {
        byte[] dbKey = new byte[TBS_INDEX_SIZE + 8 + columnValue.length];
        ByteArrayUtils.encodeInt(tbsIndex, dbKey, 0);
        ByteArrayUtils.encodeLong(segIndex, dbKey, TBS_INDEX_SIZE);
        System.arraycopy(columnValue, 0, dbKey, TBS_INDEX_SIZE + 8, columnValue.length);
        
        return dbKey;
    }
    
    @Override
    public byte[] encodeValue(HistogramSegment segment) {
        ByteBuffer bbv = ByteBuffer.allocate(RECORD_SIZE * segment.size());
        
        for (HistogramSegment.SegRecord record : segment.getRecords()) {
            bbv.putInt(record.dstart);
            bbv.putInt(record.dstop);
            bbv.putInt(record.num);
        }
        return bbv.array();
    }
    
    @Override
    public List<HistogramSegment.SegRecord> decodeValue(byte[] data, long segIndex) {
        return decodeRecords(ByteBuffer.wrap(data));
    }
    
    private List<HistogramSegment.SegRecord> decodeRecords(ByteBuffer buffer) {
        List<HistogramSegment.SegRecord> records = new ArrayList<>();
        while (buffer.hasRemaining()) {
            int dstart = buffer.getInt();
            int dstop = buffer.getInt();
            int num = buffer.getInt();
            records.add(new HistogramSegment.SegRecord(dstart, dstop, num));
        }
        return records;
    }
    
    @Override
    public HistogramSegment decodeSegment(byte[] key, byte[] value) {
        ByteBuffer keyBuffer = ByteBuffer.wrap(key);
        keyBuffer.position(TBS_INDEX_SIZE); // Skip tbsIndex
        long segIndex = keyBuffer.getLong();
        
        // Extract column value from remaining bytes in key buffer
        byte[] columnValue = new byte[keyBuffer.remaining()];
        keyBuffer.get(columnValue);
        
        List<HistogramSegment.SegRecord> decodedRecords = decodeRecords(ByteBuffer.wrap(value));
        return new HistogramSegment(columnValue, segIndex, decodedRecords);
    }
}