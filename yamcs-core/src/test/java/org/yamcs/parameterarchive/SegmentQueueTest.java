package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameterarchive.RealtimeArchiveFiller.SegmentQueue;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;

public class SegmentQueueTest {

    /** The size of an interval, in milliseconds. (2^23 seconds) */
    private static final long INTERVAL_SIZE_MILLIS = 8388608000L;

    Function<PGSegment, CompletableFuture<Void>> dbWriter = pgs -> CompletableFuture.completedFuture(null);

    @BeforeAll
    public static void beforeAll() {
        TimeEncoding.setUp();
    }

    @Test
    public void test1() {

        List<BasicParameterValue> plist1 = getParaList(9);
        List<BasicParameterValue> plist2 = getParaList(10);

        SegmentQueue sq = new SegmentQueue(1, 2, dbWriter, t -> null);
        sq.addRecord(10, new BasicParameterList(IntArray.wrap(1), plist2));
        sq.addRecord(9, new BasicParameterList(IntArray.wrap(1), plist1));

        sq.getPVSegments(0, false);
        List<ParameterValueSegment> pvsegList = sq.getPVSegments(1, true);
        System.out.println("pvsegList: " + pvsegList.get(0));

        testEquals(pvsegList, Arrays.asList(Arrays.asList(9l, 10l)));

        List<BasicParameterValue> plist3 = getParaList(11);
        sq.addRecord(11, new BasicParameterList(IntArray.wrap(1), plist3));// this will be added to a new segment
                                                                           // because the maxSegmentSize is 2

        pvsegList = sq.getPVSegments(1, true);
        testEquals(pvsegList, Arrays.asList(Arrays.asList(9l, 10l), Arrays.asList(11l)));

        pvsegList = sq.getPVSegments(1, false);// descending
        testEquals(pvsegList, Arrays.asList(Arrays.asList(11l), Arrays.asList(9l, 10l)));
    }

    @Test
    public void testAcrossIntervals() {

        long t1 = ParameterArchive.getIntervalEnd(9);
        List<BasicParameterValue> plist1 = getParaList(t1);
        List<BasicParameterValue> plist2 = getParaList(t1 + 1);

        SegmentQueue sq = new SegmentQueue(1, 2, dbWriter, t -> null);
        sq.addRecord(t1 + 1, new BasicParameterList(IntArray.wrap(1), plist2));
        sq.addRecord(t1, new BasicParameterList(IntArray.wrap(1), plist1));

        sq.getPVSegments(0, false);
        List<ParameterValueSegment> pvsegList = sq.getPVSegments(1, true);
        testEquals(pvsegList, Arrays.asList(Arrays.asList(t1), Arrays.asList(t1 + 1)));

        sq.addRecord(t1 - 1, new BasicParameterList(IntArray.wrap(1), getParaList(t1 - 1)));

        pvsegList = sq.getPVSegments(1, true);
        testEquals(pvsegList, Arrays.asList(Arrays.asList(t1 - 1, t1), Arrays.asList(t1 + 1)));

        sq.addRecord(t1 + 2, new BasicParameterList(IntArray.wrap(1), getParaList(t1 + 2)));
        pvsegList = sq.getPVSegments(1, true);
        testEquals(pvsegList, Arrays.asList(Arrays.asList(t1 - 1, t1), Arrays.asList(t1 + 1, t1 + 2)));

    }

    /**
     * Tests that with an empty queue no segments are returned.
     */
    @Test
    public void testEmptyQueue() {
        SegmentQueue sq = new SegmentQueue(1, 2, dbWriter, t -> null);
        assertTrue(sq.isEmpty());
        assertEquals(0, sq.getPVSegments(1, false).size());
        assertEquals(0, sq.getPVSegments(1, true).size());
    }

    /**
     * Tests that the queue is full when one slot is still open, so that <code>head!=tail</code>.
     */
    @Test
    public void testQueueCapacity() {
        SegmentQueue sq = new SegmentQueue(1, 2, dbWriter, t -> null);

        // Add one value in each separate interval, until the cache has only one
        // slot free.
        for (int i = 0; i < SegmentQueue.QSIZE - 1; ++i) {
            List<BasicParameterValue> plist = getParaList(i * INTERVAL_SIZE_MILLIS);
            assertTrue(sq.addRecord(i * INTERVAL_SIZE_MILLIS, new BasicParameterList(IntArray.wrap(1), plist)));
            assertEquals(i + 1, sq.size());
            assertEquals(i + 1, sq.getPVSegments(1, false).size());
            assertEquals(i + 1, sq.getPVSegments(1, true).size());
        }

        // Inserting another value in a new interval should fail, since then the queue would
        // be full, with <code>head==tail</code>, which looks the same as an empty queue.
        assertEquals(sq.size(), SegmentQueue.QSIZE - 1);
        List<BasicParameterValue> plist = getParaList(SegmentQueue.QSIZE * INTERVAL_SIZE_MILLIS);
        assertFalse(sq.addRecord(SegmentQueue.QSIZE * INTERVAL_SIZE_MILLIS,
                new BasicParameterList(IntArray.wrap(1), plist)));
        assertFalse(sq.isEmpty());

        // We should be able to retrieve all segments in the queue.
        assertEquals(SegmentQueue.QSIZE - 1, sq.getPVSegments(1, false).size());
        assertEquals(SegmentQueue.QSIZE - 1, sq.getPVSegments(1, true).size());
    }

    private void testEquals(List<ParameterValueSegment> pvsegList, List<List<Long>> l) {
        assertEquals(l.size(), pvsegList.size());

        for (int i = 0; i < l.size(); i++) {
            List<Long> l1 = l.get(i);
            ParameterValueSegment pvs = pvsegList.get(i);
            assertEquals(l1.size(), pvs.numValues());
            for (int j = 0; j < l1.size(); j++) {
                assertEquals((long) l1.get(j), pvs.getEngValue(j).getUint64Value());
            }
        }
    }

    List<BasicParameterValue> getParaList(long time) {
        ParameterValue pv = new ParameterValue("test1");
        pv.setEngValue(ValueUtility.getUint64Value(time));
        pv.setGenerationTime(time);
        return Arrays.asList(pv);
    }
}
