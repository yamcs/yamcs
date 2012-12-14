package org.yamcs.yarch;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;


import org.junit.Before;
import org.junit.Test;
import org.orekit.errors.OrekitException;
import org.yamcs.TimeInterval;
import org.yamcs.yarch.HistogramDb.HistogramIterator;
import org.yamcs.yarch.HistogramDb.Record;
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
    private void assertRecEquals(byte[] columnv, long start, long stop, int num, Record r) {
        assertTrue(Arrays.equals(columnv, r.columnv));
        assertEquals(start, r.start);
        assertEquals(stop, r.stop);
        assertEquals(num, r.num);
    }

    @Before
    public void setUp() {
        try {
            TimeEncoding.setUp();
        } catch (OrekitException e) {
            e.printStackTrace();
        }
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

    @Test
    public void testPpIndex() throws IOException {
        long g=3600*1000;
        String filename="/tmp/ppindex-test.tcb";
        (new File(filename)).delete();
        YarchDatabase ydb=YarchDatabase.getInstance(this.getClass().toString());
        HistogramDb db=new HistogramDb(ydb, filename, false);
        HistogramIterator it=db.getIterator(new TimeInterval(), -1);
        assertNull(it.getNextRecord());

        db.addValue(grp1, 1000);


        db.addValue(grp2, 1000);
        long now=System.currentTimeMillis();

        db.addValue(grp1, now);

        //    index.printDb(-1, -1, -1);
        //   index.close();

        db.addValue(grp1, g+1000);
        db.addValue(grp1, g+2000);
        db.addValue(grp2, g+1000);
        db.addValue(grp2, g+2000);
        db.addValue(grp2, g+5000);
        db.addValue(grp2, g+130000);
        db.addValue(grp1, g+4000);
        db.addValue(grp1, g+3000);

        db.printDb(new TimeInterval(), -1);
        it=db.getIterator(new TimeInterval(), -1);
        assertRecEquals(grp1, 1000, 1000, 1, it.getNextRecord());
        assertRecEquals(grp2, 1000, 1000, 1, it.getNextRecord());
        assertRecEquals(grp1, g+1000, g+4000, 4, it.getNextRecord());
        assertRecEquals(grp2, g+1000, g+2000, 2, it.getNextRecord());
        assertRecEquals(grp2, g+5000, g+5000, 1, it.getNextRecord());
        assertRecEquals(grp2, g+130000, g+130000, 1, it.getNextRecord());
        assertRecEquals(grp1, now, now, 1, it.getNextRecord());
        assertNull(it.getNextRecord());

        it=db.getIterator(new TimeInterval(), 5000);
        assertRecEquals(grp1, 1000, 1000, 1, it.getNextRecord());
        assertRecEquals(grp2, 1000, 1000, 1, it.getNextRecord());
        assertRecEquals(grp1, g+1000, g+4000, 4, it.getNextRecord());
        assertRecEquals(grp2, g+1000, g+5000, 3, it.getNextRecord());
        assertRecEquals(grp2, g+130000, g+130000, 1, it.getNextRecord());
        assertRecEquals(grp1, now, now, 1, it.getNextRecord());
        assertNull(it.getNextRecord());
    }	
}
