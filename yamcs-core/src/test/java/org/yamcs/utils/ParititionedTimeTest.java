package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

public class ParititionedTimeTest {

    @Test
    public void test1() {
        PartitionedTimeInterval<TimeInterval> pt = new PartitionedTimeInterval<>();
        assertEquals(0, pt.size());

        pt.insert(new TimeInterval(0, 1000));
        assertEquals(1, pt.size());

        TimeInterval ti = pt.insert(new TimeInterval(999, 5000));
        assertNull(ti);
        assertEquals(1, pt.size());

        ti = pt.insert(new TimeInterval(999, 5000), 1);
        assertEquals(1000, ti.getStart());

        pt.insert(new TimeInterval(6000, 7000), 0);

        ti = pt.insert(new TimeInterval(4990, 6010), 11);
        assertEquals(5000, ti.getStart());
        assertEquals(6000, ti.getEnd());

        assertEquals(4, pt.size());
        ti = pt.get(2);
        assertEquals(5000, ti.getStart());
        assertEquals(6000, ti.getEnd());
    }

    @Test
    public void test2() {
        PartitionedTimeInterval<TimeInterval> pt = new PartitionedTimeInterval<>();
        assertEquals(0, pt.size());

        pt.insert(new TimeInterval(0, 1000));
        assertEquals(1, pt.size());

        pt.insert(new TimeInterval(1000, 2000));
        assertEquals(2, pt.size());

        pt.insert(new TimeInterval(3000, 4000));
        assertEquals(3, pt.size());

        pt.insert(new TimeInterval(2000, 3000));
        assertEquals(4, pt.size());

        TimeInterval ti = pt.get(2);
        assertTiEqual(ti, 2000, 3000);

        Iterator<TimeInterval> it = pt.iterator();
        assertTrue(it.hasNext());
        assertTiEqual(it.next(), 0, 1000);
        assertTiEqual(it.next(), 1000, 2000);
        assertTiEqual(it.next(), 2000, 3000);
        assertTiEqual(it.next(), 3000, 4000);
        assertTrue(!it.hasNext());

        it = pt.reverseIterator();
        assertTrue(it.hasNext());
        assertTiEqual(it.next(), 3000, 4000);
        assertTiEqual(it.next(), 2000, 3000);
        assertTiEqual(it.next(), 1000, 2000);
        assertTiEqual(it.next(), 0, 1000);
        assertTrue(!it.hasNext());

        assertNull(pt.getFit(-1));
        assertNull(pt.getFit(7000));
        assertNull(pt.getFit(4000));
        assertEquals(pt.get(0), pt.getFit(0));
        assertEquals(pt.get(3), pt.getFit(3001));
    }

    @Test
    public void test3() {
        PartitionedTimeInterval<TimeInterval> pt = new PartitionedTimeInterval<>();
        assertEquals(0, pt.size());

        pt.insert(TimeInterval.openStart(1000));
        assertEquals(1, pt.size());
        assertTiOpenStartEqual(pt.get(0), 1000);
        TimeInterval ti = pt.insert(new TimeInterval(999, 1000));
        assertNull(ti);
        ti = pt.insert(TimeInterval.openEnd(1000));
        assertNotNull(ti);
        assertTiOpenEndEqual(pt.get(1), 1000);

        assertEquals(pt.get(1), pt.getFit(1000));
        assertEquals(pt.get(0), pt.getFit(999));
    }

    @Test
    public void test4() {
        PartitionedTimeInterval<TimeInterval> pt = new PartitionedTimeInterval<>();
        pt.insert(new TimeInterval());
        assertEquals(1, pt.size());
        assertNull(pt.insert(new TimeInterval(10, 100)));

        assertEquals(pt.get(0), pt.getFit(10000));
    }

    void assertTiOpenStartEqual(TimeInterval ti, long stop) {
        assertFalse(ti.hasStart());
        assertEquals(stop, ti.getEnd());
    }

    void assertTiOpenEndEqual(TimeInterval ti, long start) {
        assertFalse(ti.hasEnd());
        assertEquals(start, ti.getStart());
    }

    void assertTiEqual(TimeInterval ti, long start, long stop) {
        assertEquals(start, ti.getStart());
        assertEquals(stop, ti.getEnd());
    }
}
