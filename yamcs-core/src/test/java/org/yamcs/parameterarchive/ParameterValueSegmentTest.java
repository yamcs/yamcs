package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.SortedIntArray;

public class ParameterValueSegmentTest {

    @Test
    public void testGaps() {
        SortedTimeSegment ts = new SortedTimeSegment(0);
        for (int i = 0; i < 10; i++) {
            ts.add(i);
        }
        SortedIntArray gaps = new SortedIntArray(0, 1, 2, 4, 10);
        ParameterValueSegment pvs = new ParameterValueSegment(0, ts, null, null, null, gaps);

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
    }
}
