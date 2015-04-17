package org.yamcs.yarch;
import static org.junit.Assert.*;


import org.junit.Before;
import org.junit.Test;
import org.yamcs.yarch.HistogramDb.Segment;
import org.yamcs.yarch.HistogramDb.Segment.SegRecord;
import org.yamcs.utils.TimeEncoding;


public class HistogramDbTest {
    byte[] grp1="aaaaaaa".getBytes();
    byte[] grp2="b".getBytes();
    
    private void assertSegEquals(int dstart, int dstop, short num, SegRecord p) {
        assertEquals(dstart, p.dstart);
        assertEquals(dstop, p.dstop);
        assertEquals(num, p.num);
    }

    @Before
    public void setUp() {
        TimeEncoding.setUp();
    }

    @Test
    public void testDuplicate1() {
        Segment segment=new Segment("g1".getBytes(), 0);
        segment.add((short)1, (short)10, (short)10);

        segment.merge((short)10);

        assertTrue(segment.duplicate);
        assertFalse(segment.leftUpdated);

        assertFalse(segment.centerAdded);

        assertFalse(segment.rightDeleted);
        assertFalse(segment.rightUpdated);
    }

    @Test
    public void testDuplicate2() {
        Segment segment=new Segment("g2".getBytes(), 0);
        segment.add((short)1, (short)10, (short)10);
        segment.merge((short)1);

        assertTrue(segment.duplicate);
        assertFalse(segment.leftUpdated);

        assertFalse(segment.centerAdded);

        assertFalse(segment.rightDeleted);
        assertFalse(segment.rightUpdated);
    }

    @Test
    public void testInsideLeft() {
        Segment segment=new Segment("g3".getBytes(), 0);
        segment.add((short)1, (short)10, (short)2);

        segment.merge((short)9);
        assertTrue(segment.duplicate);
        assertFalse(segment.leftUpdated);

        assertFalse(segment.centerAdded);

        assertFalse(segment.rightDeleted);
        assertFalse(segment.rightUpdated);
    }

    @Test
    public void testMergeLeft1() {
        Segment segment=new Segment("g4".getBytes(), 0);
        segment.add((short)1, (short)1, (short)1);

        segment.merge((short)2);

        assertFalse(segment.duplicate);
        assertTrue(segment.leftUpdated);

        assertSegEquals((short)1, (short)2, (short)2, segment.pps.get(0));

        assertFalse(segment.centerAdded);

        assertFalse(segment.rightUpdated);
        assertFalse(segment.rightDeleted);
    }

    @Test
    public void testMergeLeft2() {
        Segment segment=new Segment("g5".getBytes(), 0);
        segment.add((short)1, (short)10, (short)10);
        segment.merge((short)11);
        assertFalse(segment.duplicate);
        assertTrue(segment.leftUpdated);

        assertSegEquals((short)1, (short)11, (short)11, segment.pps.get(0));
        assertFalse(segment.centerAdded);

        assertFalse(segment.rightUpdated);
        assertFalse(segment.rightDeleted);
    }

    @Test
    public void testMergeRight() {
        Segment segment=new Segment("g6".getBytes(), 0);
        segment.add((short)7, (short)8, (short)2);

        segment.merge((short)6);

        assertFalse(segment.duplicate);

        assertFalse(segment.leftUpdated);

        assertFalse(segment.centerAdded);


        assertTrue(segment.rightUpdated);

        assertSegEquals((short)6,(short)8, (short)3, segment.pps.get(0));
        assertFalse(segment.rightDeleted);
    }

    @Test
    public void testMergeBoth() {
        Segment segment=new Segment("g7".getBytes(), 0);
        segment.add((short)1, (short)5, (short)2);
        segment.add((short)7, (short)8, (short)2);

        segment.merge((short)6);

        assertFalse(segment.duplicate);

        assertTrue(segment.leftUpdated);
        assertSegEquals((short)1, (short) 8, (short)5, segment.pps.get(0));

        assertFalse(segment.centerAdded);

        assertTrue(segment.rightDeleted);
    }

    @Test
    public void testCheckStandalone1() {
        Segment segment=new Segment("g8".getBytes(), 0);
        segment.merge((short)11);

        assertFalse(segment.duplicate);

        assertFalse(segment.leftUpdated);


        assertTrue(segment.centerAdded);
        assertSegEquals((short)11, (short)11,(short)1, segment.pps.get(0));

        assertFalse(segment.rightUpdated);
        assertFalse(segment.rightDeleted);
    }

    @Test
    public void testCheckStandalone2() {
        Segment segment=new Segment("g9".getBytes(), 0);

        segment.add(1000,  5000, (short)2);
        segment.add(17000, 18000,(short)2);

        segment.merge(11000);

        assertFalse(segment.duplicate);

        assertFalse(segment.leftUpdated);
        assertSegEquals(1000, 5000, (short)2, segment.pps.get(0));

        assertTrue(segment.centerAdded);
        assertSegEquals(11000, 11000, (short)1, segment.pps.get(1));

        assertFalse(segment.rightUpdated);
        assertSegEquals(17000, 18000, (short)2, segment.pps.get(2));
        assertFalse(segment.rightDeleted);
    }

    @Test
    public void testSelectBestMerge() {
        Segment segment=new Segment("g10".getBytes(), 0);

        segment.add(1000, 4000, (short)4);
        segment.add(7000, 8000,(short)2);

        segment.merge(6000);

        assertFalse(segment.duplicate);

        assertFalse(segment.leftUpdated);

        assertFalse(segment.centerAdded);

        assertTrue(segment.rightUpdated);
        assertSegEquals(6000, 8000, (short)3, segment.pps.get(1));
        assertFalse(segment.rightDeleted);
    }    
}
