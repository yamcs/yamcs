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
 * the segment index (timestamp / 3600000) is encoded directly in big endian into the rocksdb key meaning that the
 * negative timestamps are sorted after the positive ones.
 * <p>
 * The delta times are stored as they are (negative for negative timestamps).
 * <p>
 * This is the format written by Yamcs before the introduction of the V2 encoding; it has to stay unchanged in order
 * to read and update the histograms of the existing tables.
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
        for (HistogramSegment.SegRecord record : segment.getRecords()) {
            bbv.putInt(record.dstart);
            bbv.putInt(record.dstop);
            bbv.putShort((short) record.num); // 16-bit limit
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
            int num = buffer.getShort() & 0xFFFF; // Convert to unsigned
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
