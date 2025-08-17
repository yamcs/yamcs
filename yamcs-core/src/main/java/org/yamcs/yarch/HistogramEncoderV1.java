package org.yamcs.yarch;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.utils.ByteArrayUtils;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

/**
 * V1 histogram encoder:
 * <p>
 * num is stored on 16 bits - cannot store more than 65536 records in a segment
 * <p>
 * timestamp encoded directly in big endian into the rocksdb key meaning that the negative timestamps are sorted before
 * the positive ones .
 */
public class HistogramEncoderV1 implements HistogramEncoder {
    
    private static final int RECORD_SIZE = 10; // 4 + 4 + 2 bytes
    private static final long GROUPING_FACTOR = 3600 * 1000; // 1 hour segments for V1
    
    @Override
    public HistogramEncoder.SplitTimeResult splitTime(long timestamp) {
        long segIndex = timestamp / GROUPING_FACTOR;
        int deltaTime = (int) (timestamp % GROUPING_FACTOR);
        return new HistogramEncoder.SplitTimeResult(segIndex, deltaTime);
    }
    
    @Override
    public long segIndex(long timestamp) {
        return timestamp / GROUPING_FACTOR;
    }
    
    @Override
    public long segmentStart(long timestamp) {
        // For V1, segment start is simply the segment boundary timestamp
        long segIndex = segIndex(timestamp);
        return segIndex * GROUPING_FACTOR;
    }
    
    @Override
    public long joinTime(long segIndex, int deltaTime) {
        return segIndex * GROUPING_FACTOR + deltaTime;
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
        long sstart = segment.segIndex(); // For V1, segIndex is the sstart value
        
        for (HistogramSegment.SegRecord record : segment.getRecords()) {
            int dstart, dstop;
            if (sstart > 0) {
                dstart = record.dstart;
                dstop = record.dstop;
            } else {
                dstart = -record.dstart;
                dstop = -record.dstop;
            }
            
            bbv.putInt(dstart);
            bbv.putInt(dstop);
            bbv.putShort((short) record.num); // 16-bit limit
        }
        return bbv.array();
    }
    
    @Override
    public List<HistogramSegment.SegRecord> decodeValue(byte[] data, long segIndex) {
        return decodeRecords(ByteBuffer.wrap(data), segIndex);
    }
    
    private List<HistogramSegment.SegRecord> decodeRecords(ByteBuffer buffer, long segIndex) {
        List<HistogramSegment.SegRecord> records = new ArrayList<>();
        long sstart = HistogramSegment.invertSignI64(segIndex);
        
        while (buffer.hasRemaining()) {
            int dstart = buffer.getInt();
            int dstop = buffer.getInt();
            int num = buffer.getShort() & 0xFFFF; // Convert to unsigned
            
            // Reverse the delta time adjustment that was done during encoding
            if (sstart <= 0) {
                dstart = -dstart;
                dstop = -dstop;
            }
            
            records.add(new HistogramSegment.SegRecord(dstart, dstop, num));
        }
        return records;
    }
    
    @Override
    public HistogramSegment decodeSegment(byte[] key, byte[] value) {
        ByteBuffer keyBuffer = ByteBuffer.wrap(key);
        keyBuffer.position(TBS_INDEX_SIZE); // Skip tbsIndex
        long sstart = keyBuffer.getLong();
        long segIndex = HistogramSegment.invertSignI64(sstart);
        
        // Extract column value from remaining bytes in key buffer
        byte[] columnValue = new byte[keyBuffer.remaining()];
        keyBuffer.get(columnValue);
        
        List<HistogramSegment.SegRecord> decodedRecords = decodeRecords(ByteBuffer.wrap(value), segIndex);
        return new HistogramSegment(columnValue, segIndex, decodedRecords);
    }
}