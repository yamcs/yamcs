package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.yamcs.cfdp.pdu.SegmentRequest;

public class MissingSegmentsTest {

    @Test
    public void testAddMerge() {
        MissingSegments ms = new MissingSegments();
        ms.add(10, 20);
        ms.add(30, 40);
        assertEquals("[10-20, 30-40]", ms.toString());

        ms.add(20, 25); // adjacent
        assertEquals("[10-25, 30-40]", ms.toString());

        ms.add(5, 35); // spans both
        assertEquals("[5-40]", ms.toString());

        ms.add(5, 5); // empty
        assertEquals("[5-40]", ms.toString());
    }

    @Test
    public void testRemoveInside() {
        MissingSegments ms = new MissingSegments();
        ms.add(10, 20);
        ms.add(30, 40);
        ms.remove(0, 50);
        assertEquals("[]", ms.toString());
    }

    @Test
    public void testRemoveTruncate() {
        MissingSegments ms = new MissingSegments();
        ms.add(10, 20);
        ms.add(30, 40);
        ms.add(50, 60);
        ms.remove(15, 35);
        assertEquals("[10-15, 35-40, 50-60]", ms.toString());
    }

    @Test
    public void testRemoveSplit() {
        MissingSegments ms = new MissingSegments();
        ms.add(10, 40);
        ms.remove(20, 30);
        assertEquals("[10-20, 30-40]", ms.toString());
    }

    @Test
    public void testRemoveSameStart() {
        MissingSegments ms = new MissingSegments();
        ms.add(10, 40);
        ms.remove(10, 20);
        assertEquals("[20-40]", ms.toString());
        ms.remove(30, 40);
        assertEquals("[20-30]", ms.toString());
    }

    @Test
    public void testNakReplacesScope() {
        MissingSegments ms = new MissingSegments();
        // first NAK, scope [0, 100)
        ms.add(10, 20);
        ms.add(50, 60);
        ms.add(90, 100);

        // second NAK, scope [0, 70), only 55-58 still missing
        ms.remove(0, 70);
        ms.add(55, 58);
        assertEquals("[55-58, 90-100]", ms.toString());
    }

    @Test
    public void testPoll() {
        MissingSegments ms = new MissingSegments();
        ms.add(10, 35);
        ms.add(50, 52);

        assertSegment(10, 20, ms.poll(10));
        assertSegment(20, 30, ms.poll(10));
        assertSegment(30, 35, ms.poll(10));
        assertSegment(50, 52, ms.poll(10));
        assertNull(ms.poll(10));
    }

    private void assertSegment(long start, long end, SegmentRequest s) {
        assertEquals(start, s.getSegmentStart());
        assertEquals(end, s.getSegmentEnd());
    }
}
