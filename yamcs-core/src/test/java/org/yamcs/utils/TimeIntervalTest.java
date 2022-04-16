package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeInterval.FilterOverlappingIterator;

public class TimeIntervalTest {

    private void checkElements(Iterator<TimeInterval> it, List<TimeInterval> list, int... indices) {
        for (int i : indices) {
            assertTrue(it.hasNext());
            assertEquals(list.get(i), it.next());
        }
        assertTrue(!it.hasNext());
    }

    @Test
    public void testOverlappingTailIterator() {
        List<TimeInterval> list = new ArrayList<>();
        list.add(new TimeInterval(10, 20));
        list.add(new TimeInterval(20, 30));
        list.add(new TimeInterval(50, 100));

        Iterator<TimeInterval> it;

        it = new FilterOverlappingIterator<>(new TimeInterval(), list.iterator());
        checkElements(it, list, 0, 1, 2);

        it = new FilterOverlappingIterator<>(new TimeInterval(10, 100), list.iterator());
        checkElements(it, list, 0, 1, 2);

        it = new FilterOverlappingIterator<>(new TimeInterval(1, 2), list.iterator());
        checkElements(it, list);

        it = new FilterOverlappingIterator<>(new TimeInterval(1, 10), list.iterator());
        checkElements(it, list, 0);

        it = new FilterOverlappingIterator<>(new TimeInterval(10, 10), list.iterator());
        checkElements(it, list, 0);

        it = new FilterOverlappingIterator<>(new TimeInterval(41, 42), list.iterator());
        checkElements(it, list);

        it = new FilterOverlappingIterator<>(new TimeInterval(100, 102), list.iterator());
        checkElements(it, list);

        it = new FilterOverlappingIterator<>(new TimeInterval(30, 50), list.iterator());
        checkElements(it, list, 2);

        it = new FilterOverlappingIterator<>(new TimeInterval(25, 50), list.iterator());
        checkElements(it, list, 1, 2);

        it = new FilterOverlappingIterator<>(new TimeInterval(200, 300), list.iterator());
        checkElements(it, list);
    }
}
