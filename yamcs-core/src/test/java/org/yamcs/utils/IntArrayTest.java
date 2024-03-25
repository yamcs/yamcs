package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class IntArrayTest {
    static Random rand = new Random();

    @Test
    public void testSort1() {
        IntArray s1 = IntArray.wrap(3, 9, 5);
        List<Integer> l = toList(s1);
        s1.sort(l);
        assertEquals(3, s1.size());

        checkSortedAndEqual(s1, l);
    }

    @Test
    public void testSortSorted50000() {
        int n = 50000;
        IntArray a = new IntArray();
        for (int i = 0; i < n; i++) {
            a.add(1);
        }
        List<Integer> l = toList(a);
        a.sort(l);
        assertEquals(n, a.size());

        checkSortedAndEqual(a, l);
    }

    @Test
    public void testSortEquals() {
        IntArray s1 = IntArray.wrap(3, 3, 3);
        List<Integer> l = toList(s1);
        s1.sort(l);
        assertEquals(3, s1.size());

        checkSortedAndEqual(s1, l);
    }

    @Test
    public void testSortEmpty() {
        IntArray a = new IntArray();
        List<Integer> l = toList(a);
        a.sort(l);
        assertEquals(0, a.size());
    }

    @Test
    public void testSort1000() {
        int n = 1000;
        IntArray a = new IntArray();
        for (int i = 0; i < n; i++) {
            a.add(rand.nextInt());
        }
        List<Integer> l = toList(a);
        a.sort(l);
        assertEquals(n, a.size());

        checkSortedAndEqual(a, l);
    }

    @Test
    public void testRemove() {
        int n = 10;
        IntArray a = new IntArray();
        for (int i = 0; i < n; i++) {
            a.add(i);
        }
        List<Integer> l = toList(a);
        a.remove(0);
        l.remove(0);

        assertEquals(l, toList(a));

        a.remove(a.size() - 1);
        l.remove(l.size() - 1);
        assertEquals(l, toList(a));

        a.remove(3);
        l.remove(3);
        assertEquals(l, toList(a));

    }

    @Test
    public void testBinarySearch() {
        IntArray s1 = IntArray.wrap(1, 4, 5);
        assertEquals(0, s1.binarySearch(1));
        assertEquals(2, s1.binarySearch(5));

        assertEquals(-4, s1.binarySearch(7));

        assertEquals(-1, s1.binarySearch(-10));
    }

    @Test
    public void testIntersection1() {
        IntArray s1 = IntArray.wrap(3, 5, 9);
        IntArray s2 = IntArray.wrap(3, 20);
        assertEquals(1, s1.intersectionSize(s2));
    }

    @Test
    public void testIntersection2() {
        IntArray s1 = IntArray.wrap(3, 3, 3, 5, 9);
        IntArray s2 = IntArray.wrap(3, 3, 20);
        assertEquals(2, s2.intersectionSize(s1));
    }

    @Test
    public void testUnion() {
        IntArray s1 = IntArray.wrap(3, 3, 3, 5, 9);
        IntArray s2 = IntArray.wrap(3, 3, 20);
        IntArray s3 = IntArray.union(s1, s2, 0);

        assertEquals(IntArray.wrap(3, 3, 3, 5, 9, 20), s3);
    }

    private void checkSortedAndEqual(IntArray a, List<Integer> l) {
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i), (int) l.get(i));
            if (i > 0) {
                assertTrue(a.get(i - 1) <= a.get(i));
            }
        }
    }

    @Test
    public void testCompare() {
        assertEquals(0, IntArray.compare(IntArray.wrap(), IntArray.wrap()));

        assertEquals(0, IntArray.compare(IntArray.wrap(1), IntArray.wrap(1)));

        assertEquals(2, IntArray.compare(IntArray.wrap(1), IntArray.wrap()));

        assertEquals(1, IntArray.compare(IntArray.wrap(), IntArray.wrap(1)));

        assertEquals(1, IntArray.compare(IntArray.wrap(1), IntArray.wrap(1, 2)));

        assertEquals(2, IntArray.compare(IntArray.wrap(1, 2), IntArray.wrap(1)));

        assertEquals(1, IntArray.compare(IntArray.wrap(1, 2, 4), IntArray.wrap(1, 2, 3, 4)));

        assertEquals(2, IntArray.compare(IntArray.wrap(1, 2, 3, 4), IntArray.wrap(1, 2, 4)));

        assertEquals(1, IntArray.compare(IntArray.wrap(2, 4), IntArray.wrap(1, 2, 3, 4)));

        assertEquals(2, IntArray.compare(IntArray.wrap(1, 2, 3, 4), IntArray.wrap(2, 4)));

        assertEquals(-1, IntArray.compare(IntArray.wrap(1), IntArray.wrap(2)));

        assertEquals(-1, IntArray.compare(IntArray.wrap(1, 2), IntArray.wrap(1, 3)));

    }

    List<Integer> toList(IntArray a) {
        return a.stream().boxed().collect(Collectors.toList());
    }
}
