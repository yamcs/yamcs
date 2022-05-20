package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.HistogramSegment.SegRecord;

public class HistogramSegmentTest {
    byte[] grp1 = "aaaaaaa".getBytes();
    byte[] grp2 = "b".getBytes();

    @BeforeEach
    public void setUp() {
        TimeEncoding.setUp();
    }

    @Test
    public void testUnordered() {
        HistogramSegment segment = new HistogramSegment("g1".getBytes(), 0);
        segment.merge(1000);
        segment.merge(2000);
        assertEquals(1, segment.size());

        segment.merge(1);

        assertEquals(1, segment.size());
        assertSegEquals(1, 2000, 3, segment.pps.get(0));

    }

    @Test
    public void testInside1() {
        HistogramSegment segment = new HistogramSegment("g1".getBytes(), 0);
        segment.add(1, 10, (short) 10);

        segment.merge((short) 10);
        assertEquals(1, segment.size());
        assertSegEquals(1, 10, 11, segment.pps.get(0));
    }

    @Test
    public void testInside2() {
        HistogramSegment segment = new HistogramSegment("g2".getBytes(), 0);
        segment.add((short) 1, (short) 10, (short) 10);
        segment.merge((short) 1);

        assertEquals(1, segment.size());
        assertSegEquals(1, 10, 11, segment.pps.get(0));
    }

    @Test
    public void testInside3() {
        HistogramSegment segment = new HistogramSegment("g3".getBytes(), 0);
        segment.add((short) 1, (short) 10, (short) 2);

        segment.merge((short) 9);

        assertEquals(1, segment.size());
        assertSegEquals(1, 10, 3, segment.pps.get(0));
    }

    @Test
    public void testMergeLeft1() {
        HistogramSegment segment = new HistogramSegment("g4".getBytes(), 0);
        segment.add((short) 1, (short) 1, (short) 1);
        segment.merge((short) 2);

        assertEquals(1, segment.size());
        assertSegEquals(1, 2, 2, segment.pps.get(0));
    }

    @Test
    public void testMergeLeft2() {
        HistogramSegment segment = new HistogramSegment("g5".getBytes(), 0);
        segment.add((short) 1, (short) 10, (short) 10);
        segment.merge((short) 11);

        assertEquals(1, segment.size());
        assertSegEquals((short) 1, (short) 11, (short) 11, segment.pps.get(0));
    }

    @Test
    public void testMergeRight() {
        HistogramSegment segment = new HistogramSegment("g6".getBytes(), 0);
        segment.add((short) 7, (short) 8, (short) 2);

        segment.merge((short) 6);

        assertEquals(1, segment.size());
        assertSegEquals((short) 6, (short) 8, (short) 3, segment.pps.get(0));
    }

    @Test
    public void testMergeBoth() {
        HistogramSegment segment = new HistogramSegment("g7".getBytes(), 0);
        segment.add((short) 1, (short) 5, (short) 2);
        segment.add((short) 7, (short) 8, (short) 2);

        segment.merge((short) 6);

        assertEquals(1, segment.size());
        assertSegEquals((short) 1, (short) 8, (short) 5, segment.pps.get(0));
    }

    @Test
    public void testCheckStandalone1() {
        HistogramSegment segment = new HistogramSegment("g8".getBytes(), 0);
        segment.merge((short) 11);

        assertEquals(1, segment.size());
        assertSegEquals((short) 11, (short) 11, (short) 1, segment.pps.get(0));
    }

    @Test
    public void testCheckStandalone2() {
        HistogramSegment segment = new HistogramSegment("g9".getBytes(), 0);

        segment.add(1000, 5000, (short) 2);
        segment.add(17000, 18000, (short) 2);

        segment.merge(11000);

        assertEquals(3, segment.size());

        assertSegEquals(1000, 5000, (short) 2, segment.pps.get(0));
        assertSegEquals(11000, 11000, (short) 1, segment.pps.get(1));
        assertSegEquals(17000, 18000, (short) 2, segment.pps.get(2));
    }

    @Test
    public void testSelectBestMerge() {
        HistogramSegment segment = new HistogramSegment("g10".getBytes(), 0);

        segment.add(1000, 4000, (short) 4);
        segment.add(7000, 8000, (short) 2);

        segment.merge(6000);

        assertEquals(2, segment.size());

        assertSegEquals(1000, 4000, (short) 4, segment.pps.get(0));
        assertSegEquals(6000, 8000, (short) 3, segment.pps.get(1));
    }

    @Test
    public void testSelectBestMerge1() {
        HistogramSegment segment = new HistogramSegment("g10".getBytes(), 0);

        segment.add(1000, 4000, (short) 4);
        segment.add(7000, 8000, (short) 2);

        segment.merge(4000);

        assertEquals(2, segment.size());

        assertSegEquals(1000, 4000, (short) 5, segment.pps.get(0));
        assertSegEquals(7000, 8000, (short) 2, segment.pps.get(1));
    }

    private void assertSegEquals(int dstart, int dstop, int num, SegRecord p) {
        assertEquals(dstart, p.dstart);
        assertEquals(dstop, p.dstop);
        assertEquals(num, p.num);
    }
}
