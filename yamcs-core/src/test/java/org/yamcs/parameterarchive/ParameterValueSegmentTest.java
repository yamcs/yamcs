package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.PeekingIterator;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.ValueUtility;

public class ParameterValueSegmentTest {

    @Test
    public void testGaps() {
        SortedTimeSegment ts = new SortedTimeSegment(0);
        for (int i = 0; i < 10; i++) {
            ts.add(i);
        }
        ts.add(11);
        ts.add(12);
        SortedIntArray gaps = new SortedIntArray(0, 1, 2, 4, 10);

        // t: 0 1 2 3 4 5 6 7 8 9 11 12
        // v: - - - 0 - 1 2 3 4 5 -- 6
        // g: 0 1 2 4 10
        ParameterValueSegment pvs = getPvs(ts, gaps);

        assertEquals(-1, pvs.previousBeforeGap(0));
        assertEquals(-1, pvs.previousBeforeGap(1));
        assertEquals(-1, pvs.previousBeforeGap(2));
        assertEquals(0, pvs.previousBeforeGap(3));
        assertEquals(0, pvs.previousBeforeGap(4));
        assertEquals(1, pvs.previousBeforeGap(5));
        assertEquals(5, pvs.previousBeforeGap(10));

        assertEquals(0, pvs.nextAfterGap(0));
        assertEquals(0, pvs.nextAfterGap(1));
        assertEquals(0, pvs.nextAfterGap(2));
        assertEquals(0, pvs.nextAfterGap(3));
        assertEquals(1, pvs.nextAfterGap(4));
        assertEquals(1, pvs.nextAfterGap(5));
        assertEquals(6, pvs.nextAfterGap(10));

        var it1a = pvs.newAscendingIterator(0);
        checkEquals(new long[] { 3, 5, 6, 7, 8, 9, 12 }, getAll(it1a));

        var it1d = pvs.newDescendingIterator(0);
        checkEquals(new long[] {}, getAll(it1d));

        var it2a = pvs.newAscendingIterator(2);
        checkEquals(new long[] { 3, 5, 6, 7, 8, 9, 12 }, getAll(it2a));

        var it2d = pvs.newDescendingIterator(2);
        checkEquals(new long[] {}, getAll(it2d));

        var it3a = pvs.newAscendingIterator(3);
        checkEquals(new long[] { 3, 5, 6, 7, 8, 9, 12 }, getAll(it3a));

        var it3d = pvs.newDescendingIterator(3);
        checkEquals(new long[] { 3 }, getAll(it3d));

        var it4a = pvs.newAscendingIterator(4);
        checkEquals(new long[] { 5, 6, 7, 8, 9, 12 }, getAll(it4a));

        var it4d = pvs.newDescendingIterator(4);
        checkEquals(new long[] { 3 }, getAll(it4d));

        var it9a = pvs.newAscendingIterator(9);
        checkEquals(new long[] { 9, 12 }, getAll(it9a));

        var it9d = pvs.newDescendingIterator(9);
        checkEquals(new long[] { 9, 8, 7, 6, 5, 3 }, getAll(it9d));

        var it10a = pvs.newAscendingIterator(10);
        checkEquals(new long[] { 12 }, getAll(it10a));

        var it10d = pvs.newDescendingIterator(10);
        checkEquals(new long[] { 9, 8, 7, 6, 5, 3 }, getAll(it10d));
    }

    @Test
    public void testGaps2() {
        SortedTimeSegment ts = new SortedTimeSegment(0);
        ts.add(1);
        ts.add(3);

        SortedIntArray gaps = new SortedIntArray();
        gaps.insert(1);

        ParameterValueSegment pvs = getPvs(ts, gaps);

        var it2d = pvs.newDescendingIterator(4);
        checkEquals(new long[] { 1 }, getAll(it2d));
    }


    @Test
    public void testWithoutGaps() {
        SortedTimeSegment ts = new SortedTimeSegment(0);
        ts.add(1);
        ts.add(2);
        ts.add(4);
        ParameterValueSegment pvs = getPvs(ts, null);

        var it0a = pvs.newAscendingIterator(0);
        checkEquals(new long[] { 1, 2, 4 }, getAll(it0a));
        var it0d = pvs.newDescendingIterator(0);
        checkEquals(new long[] {}, getAll(it0d));

        var it1a = pvs.newAscendingIterator(1);
        checkEquals(new long[] { 1, 2, 4 }, getAll(it1a));
        var it1d = pvs.newDescendingIterator(1);
        checkEquals(new long[] { 1 }, getAll(it1d));

        var it2a = pvs.newAscendingIterator(2);
        checkEquals(new long[] { 2, 4 }, getAll(it2a));
        var it2d = pvs.newDescendingIterator(2);
        checkEquals(new long[] { 2, 1 }, getAll(it2d));

        var it3a = pvs.newAscendingIterator(3);
        checkEquals(new long[] { 4 }, getAll(it3a));
        var it3d = pvs.newDescendingIterator(3);
        checkEquals(new long[] { 2, 1 }, getAll(it3d));

        var it4a = pvs.newAscendingIterator(4);
        checkEquals(new long[] { 4 }, getAll(it4a));
        var it4d = pvs.newDescendingIterator(4);
        checkEquals(new long[] { 4, 2, 1 }, getAll(it4d));

        var it5a = pvs.newAscendingIterator(5);
        checkEquals(new long[] {}, getAll(it5a));
        var it5d = pvs.newDescendingIterator(5);
        checkEquals(new long[] { 4, 2, 1 }, getAll(it5d));
    }

    List<TimedValue> getAll(PeekingIterator<TimedValue> it) {
        var r = new ArrayList<TimedValue>();
        while (it.isValid()) {
            r.add(it.value());
            it.next();
        }
        return r;
    }

    private void checkEquals(long[] ls, List<TimedValue> values) {
        assertEquals(ls.length, values.size());
        for (int i = 0; i < ls.length; i++) {
            var tv = values.get(i);
            assertEquals(ls[i], tv.instant);
            assertEquals(ls[i], tv.engValue.getUint64Value());
        }

    }

    private ParameterValueSegment getPvs(SortedTimeSegment ts, SortedIntArray gaps) {
        ValueSegment vs = new LongValueSegment(Type.UINT64);
        for (int i = 0; i < ts.size(); i++) {
            if (gaps == null || !gaps.contains(i)) {
                vs.add(ValueUtility.getIntValue(64, false, ts.getTime(i)));
            }
        }
        return new ParameterValueSegment(0, ts, vs, null, null, gaps);
    }

}
