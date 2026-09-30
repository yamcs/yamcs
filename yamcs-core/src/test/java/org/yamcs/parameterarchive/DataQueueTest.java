package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameterarchive.RealtimeArchiveFiller.DataQueue;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;

import static org.yamcs.parameterarchive.RealtimeArchiveFiller.DataQueue.IntervalData.QSIZE;

public class DataQueueTest {

    /** The size of an interval, in milliseconds. (2^23 seconds) */
    private static final long INTERVAL_SIZE_MILLIS = 8388608000L;

    Function<PGSegment, CompletableFuture<Void>> dbWriter = pgs -> CompletableFuture.completedFuture(null);
    FillerLock fillerLock;

    @BeforeAll
    public static void beforeAll() {
        TimeEncoding.setUp();
    }

    @BeforeEach
    public void beforeEach() {
        fillerLock = new FillerLock();
    }

    @Test
    public void test1() {

        List<BasicParameterValue> plist1 = getParaList(9);
        List<BasicParameterValue> plist2 = getParaList(10);

        DataQueue sq = new DataQueue(1, 2, dbWriter, t -> null, fillerLock);
        sq.addRecord(10, new BasicParameterList(IntArray.wrap(1), plist2));
        sq.addRecord(9, new BasicParameterList(IntArray.wrap(1), plist1));

        sq.getPVSegments(0, false);
        List<ParameterValueSegment> pvsegList = sq.getPVSegments(1, true);

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

        DataQueue sq = new DataQueue(1, 2, dbWriter, t -> null, fillerLock);
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
        DataQueue sq = new DataQueue(1, 2, dbWriter, t -> null, fillerLock);
        assertFalse(sq.hasDataToWrite());
        assertEquals(0, sq.getPVSegments(1, false).size());
        assertEquals(0, sq.getPVSegments(1, true).size());
    }

    @Test
    public void testMultiParameterSegments() {
        DataQueue sq = new DataQueue(1, 2, dbWriter, t -> null, fillerLock);
        sq.addRecord(9, new BasicParameterList(IntArray.wrap(1), getParaList(9)));
        sq.addRecord(10, new BasicParameterList(IntArray.wrap(1), getParaList(10)));
        sq.addRecord(11, new BasicParameterList(IntArray.wrap(1), getParaList(11)));

        ParameterId[] pids = { new TestParameterId(1) };
        List<MultiParameterValueSegment> ascending = sq.getPVSegments(pids, true);
        assertEquals(2, ascending.size());
        assertEquals(9, ascending.get(0).getSegmentStart());
        assertEquals(11, ascending.get(1).getSegmentStart());

        List<MultiParameterValueSegment> descending = sq.getPVSegments(pids, false);
        assertEquals(2, descending.size());
        assertEquals(11, descending.get(0).getSegmentStart());
        assertEquals(9, descending.get(1).getSegmentStart());
    }

    @Test
    public void testMultiParameterAscendingSkipsClearedSlot() {
        DataQueue sq = new DataQueue(1, 1, dbWriter, t -> null, fillerLock);
        sq.addRecord(9, new BasicParameterList(IntArray.wrap(1), getParaList(9)));
        sq.addRecord(10, new BasicParameterList(IntArray.wrap(1), getParaList(10)));

        // Model an asynchronous archive write clearing a slot after traversal has selected its starting position.
        sq.intervals.get(0).segments[0] = null;

        ParameterId[] pids = { new TestParameterId(1) };
        List<MultiParameterValueSegment> ascending = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> sq.getPVSegments(pids, true));
        assertEquals(1, ascending.size());
        assertEquals(10, ascending.get(0).getSegmentStart());
    }

    /**
     * Tests that the queue is full when one slot is still open, so that <code>head!=tail</code>.
     */
    @Test
    public void testQueueCapacity() {
        DataQueue sq = new DataQueue(1, 2, dbWriter, t -> null, fillerLock);

        // Add two values in each separate interval, until the cache has only one
        // slot free.
        for (int i = 0; i < QSIZE - 1; ++i) {
            long t = 2 * i;
            List<BasicParameterValue> plist0 = getParaList(t);
            assertTrue(sq.addRecord(t, new BasicParameterList(IntArray.wrap(1), plist0)));

            List<BasicParameterValue> plist1 = getParaList(t + 1);
            assertTrue(sq.addRecord(t + 1, new BasicParameterList(IntArray.wrap(1), plist1)));

            assertEquals(i + 1, sq.numReadSegments());
            assertEquals(i + 1, sq.getPVSegments(1, false).size());
            assertEquals(i + 1, sq.getPVSegments(1, true).size());
        }

        // Inserting another value in a new interval should fail, since then the queue would
        // be full, with <code>head==tail</code>, which looks the same as an empty queue.
        assertEquals(sq.numReadSegments(), QSIZE - 1);
        List<BasicParameterValue> plist = getParaList(QSIZE * INTERVAL_SIZE_MILLIS);
        assertFalse(sq.addRecord(QSIZE * 2, new BasicParameterList(IntArray.wrap(1), plist)));
        assertTrue(sq.hasDataToWrite());

        // We should be able to retrieve all segments in the queue.
        assertEquals(QSIZE - 1, sq.getPVSegments(1, false).size());
        assertEquals(QSIZE - 1, sq.getPVSegments(1, true).size());
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

    private record TestParameterId(int pid) implements ParameterId {

        @Override
        public Type getRawType() {
            return Type.UINT64;
        }

        @Override
        public Type getEngType() {
            return Type.UINT64;
        }

        @Override
        public int getPid() {
            return pid;
        }

        @Override
        public String getParamFqn() {
            return "/test/test1";
        }

        @Override
        public boolean isSimple() {
            return true;
        }

        @Override
        public boolean hasRawValue() {
            return false;
        }

        @Override
        public IntArray getComponents() {
            return null;
        }
    }
}
