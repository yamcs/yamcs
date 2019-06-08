package org.yamcs.utils;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.Test;

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
        for(int i=0; i<n; i++) {
           a.add(1);
        }
        List<Integer> l = toList(a);
        a.sort(l);
        System.out.println("a.count: "+a.count);
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
        for(int i=0; i<n; i++) {
           a.add(rand.nextInt());
        }
        List<Integer> l = toList(a);
        a.sort(l);
        assertEquals(n, a.size());
    
        checkSortedAndEqual(a, l);
    }
    
    @Test
    public void testBinarySearch() {
        IntArray s1 = IntArray.wrap(1,4,5);
        assertEquals(0, s1.binarySearch(1));
        assertEquals(2, s1.binarySearch(5));
        
        assertEquals(-4, s1.binarySearch(7));
        
        assertEquals(-1, s1.binarySearch(-10));
    }
    
    private void checkSortedAndEqual(IntArray a, List<Integer> l) {
        for(int i=0; i<a.size(); i++) {
            assertEquals(a.get(i), (int)l.get(i));
            if(i>0) {
                assertTrue(a.get(i-1) <= a.get(i));
            }
        }
        
    }

    List<Integer> toList(IntArray a) {
        return a.stream().boxed().collect(Collectors.toList());
    }
}
