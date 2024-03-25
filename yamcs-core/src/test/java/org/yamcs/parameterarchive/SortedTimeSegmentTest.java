package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.SortedIntArray;

public class SortedTimeSegmentTest {

    @Test
    public void test1() {
        SortedTimeSegment sts = new SortedTimeSegment(0);
        for (long i = 0; i < 10; i++) {
            sts.add(i);
        }

        SortedIntArray gaps = new SortedIntArray(2, 3, 6);
        long[] x = sts.getRange(1, 8, true);
        long[] xg = sts.getRangeWithGaps(1, 8, true, gaps);
        assertArrayEquals(new long[] { 1, 2, 3, 4, 5, 6, 7 }, x);
        assertArrayEquals(new long[] { 1, 4, 5, 7 }, xg);

        long[] xr = sts.getRange(1, 8, false);
        long[] xrg = sts.getRangeWithGaps(1, 8, false, gaps);

        assertArrayEquals(new long[] { 8, 7, 6, 5, 4, 3, 2 }, xr);
        assertArrayEquals(new long[] { 8, 7, 5, 4 }, xrg);

    }
}
