package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The V1 encoding has to stay identical to the format written by the Yamcs versions before the V2 encoding, otherwise
 * the histograms of the existing tables cannot be read or updated.
 */
public class HistogramEncoderV1Test {
    static final long GROUPING_FACTOR = 3600 * 1000;
    static final int TBS_INDEX = 7;
    static final byte[] COLUMNV = { 1, 2, 3 };

    HistogramEncoder encoder = new HistogramEncoderV1();

    @Test
    public void testPositiveTime() {
        checkLegacyFormat(1_704_067_200_000L + 1_234_000L);
    }

    @Test
    public void testNegativeTime() {
        checkLegacyFormat(-1_704_067_200_000L - 1_234_000L);
    }

    @Test
    public void testFirstSegment() {
        checkLegacyFormat(1_234_000L);
    }

    private void checkLegacyFormat(long t) {
        byte[] legacyKey = legacyKey(t);
        byte[] legacyValue = legacyValue(t % GROUPING_FACTOR, t % GROUPING_FACTOR, 1);

        // write path
        var split = encoder.splitTime(t);
        assertArrayEquals(legacyKey, encoder.encodeKey(TBS_INDEX, split.segIndex(), COLUMNV));
        HistogramSegment segment = new HistogramSegment(COLUMNV, split.segIndex());
        segment.merge(split.deltaTime());
        assertArrayEquals(legacyValue, encoder.encodeValue(segment));

        // read path (iterator)
        HistogramSegment decoded = encoder.decodeSegment(legacyKey, legacyValue);
        assertEquals(t / GROUPING_FACTOR, decoded.segIndex());
        assertArrayEquals(COLUMNV, decoded.columnv());
        assertEquals(1, decoded.size());
        HistogramSegment.SegRecord rec = decoded.getRecords().get(0);
        assertEquals(t, encoder.joinTime(decoded.segIndex(), rec.dstart()));
        assertEquals(t, encoder.joinTime(decoded.segIndex(), rec.dstop()));
        assertEquals(1, rec.num());

        // update path (writer reloading an existing segment from the database and adding one more record)
        long t2 = t + 1000;
        var split2 = encoder.splitTime(t2);
        assertEquals(split.segIndex(), split2.segIndex());
        List<HistogramSegment.SegRecord> records = encoder.decodeValue(legacyValue, split2.segIndex());
        HistogramSegment reloaded = new HistogramSegment(COLUMNV, split2.segIndex(), records);
        reloaded.merge(split2.deltaTime());
        long dmin = Math.min(t, t2) % GROUPING_FACTOR;
        long dmax = Math.max(t, t2) % GROUPING_FACTOR;
        assertArrayEquals(legacyValue(dmin, dmax, 2), encoder.encodeValue(reloaded));
    }

    // key as written by RdbHistogramInfo.histoDbKey before the V2 encoding
    private static byte[] legacyKey(long t) {
        ByteBuffer bb = ByteBuffer.allocate(4 + 8 + COLUMNV.length);
        bb.putInt(TBS_INDEX);
        bb.putLong(t / GROUPING_FACTOR);
        bb.put(COLUMNV);
        return bb.array();
    }

    // value as written by HistogramSegment.val() before the V2 encoding
    private static byte[] legacyValue(long dstart, long dstop, int num) {
        ByteBuffer bb = ByteBuffer.allocate(10);
        bb.putInt((int) dstart);
        bb.putInt((int) dstop);
        bb.putShort((short) num);
        return bb.array();
    }
}
