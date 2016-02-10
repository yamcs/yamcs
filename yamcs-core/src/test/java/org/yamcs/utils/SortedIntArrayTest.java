package org.yamcs.utils;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.PrimitiveIterator;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;

public class SortedIntArrayTest {
    @Test
    public void test1() {
        SortedIntArray s = new SortedIntArray();
        assertEquals(0, s.size());

        s.insert(2);
        assertEquals(1, s.size());
        assertEquals(2, s.get(0));

        s.insert(3);
        assertEquals(2, s.size());
        assertEquals(2, s.get(0));
        assertEquals(3, s.get(1));

        s.insert(1);
        assertEquals(3, s.size());
        assertEquals(1, s.get(0));
        assertEquals(2, s.get(1));
        assertEquals(3, s.get(2));

    }

    @Test
    public void test2() {
        SortedIntArray s = new SortedIntArray();
        assertEquals(0, s.size());
        int n = 1000;
        for(int i =0; i<n/2; i++) {
            s.insert(i);
        }
        for(int i=n-1; i>=n/2; i--) {
            s.insert(i);
        }
        assertEquals(n, s.size());

        for(int i=0;i<n;i++) {
            assertEquals(i, s.get(i));
        }
    }
    
    @Test
    public void testEquals() {
        SortedIntArray s1 = new SortedIntArray(1, 3, 7);
        SortedIntArray s2 = new SortedIntArray(3, 1, 7);
        SortedIntArray s3 = new SortedIntArray(1, 3);
        assertEquals(s1.hashCode(), s2.hashCode());
        
        assertTrue(s1.equals(s2));
        assertTrue(s2.equals(s1));
        
        assertFalse(s1.equals(s3));
        assertFalse(s3.equals(s1));
        
        assertFalse(s1.hashCode() == s3.hashCode());
    }
    
    @Test
    public void testEncodeDecode() {
        SortedIntArray s1 = new SortedIntArray(1,5,20);
        byte[] encoded = s1.encodeToVarIntArray();
        SortedIntArray s2 = SortedIntArray.decodeFromVarIntArray(encoded);
        assertTrue(s1.equals(s2));
    }
    
    @Test
    public void testEncodeDecodeNegative() {
        SortedIntArray s1 = new SortedIntArray(-1, 5, 20);
        byte[] encoded = s1.encodeToVarIntArray();
        SortedIntArray s2 = SortedIntArray.decodeFromVarIntArray(encoded);
        System.out.println("s2 encoded: "+s2);
        
        assertTrue(s1.equals(s2));
    }
    @Test
    public void testEncodeDecodeZeroLength() {
        SortedIntArray s1 = new SortedIntArray();
        byte[] encoded = s1.encodeToVarIntArray();
        SortedIntArray s2 = SortedIntArray.decodeFromVarIntArray(encoded);
        assertTrue(s1.equals(s2));
        
        assertTrue(s2.equals(s1));
    }
    
    private void assertItEquals(PrimitiveIterator.OfInt it, int ...a) {
        for(int i=0; i<a.length;i++) {
            assertTrue(it.hasNext());
            assertEquals(a[i], it.nextInt());
        }
        assertFalse(it.hasNext());
    }
    
    @Test
    public void testAscendingIterator() {
        SortedIntArray s1 = new SortedIntArray(1,3,4);
        
        PrimitiveIterator.OfInt it = s1.getAscendingIterator(0);
        assertItEquals(it, 1, 3, 4);
        
        it = s1.getAscendingIterator(1);
        assertItEquals(it, 1, 3, 4);
        
        it = s1.getAscendingIterator(2);
        assertItEquals(it, 3, 4);
        
        it = s1.getAscendingIterator(4);
        assertItEquals(it, 4);
        
        it = s1.getAscendingIterator(5);
        assertFalse(it.hasNext());
    }
    
    @Test
    public void testDescendingIterator() {
        SortedIntArray s1 = new SortedIntArray(1, 3, 4);
        
        PrimitiveIterator.OfInt it = s1.getDescendingIterator(0);
        assertFalse(it.hasNext());
        
        it = s1.getDescendingIterator(1);
        assertFalse(it.hasNext());
        
        it = s1.getDescendingIterator(2);
        assertItEquals(it, 1);
        
        it = s1.getDescendingIterator(4);
        assertItEquals(it, 3, 1);
        
        it = s1.getDescendingIterator(5);
        assertItEquals(it, 4, 3, 1);
    }
    
    @Test
    public void testIteratorsEmptyArray() {
        SortedIntArray s1 = new SortedIntArray();
        PrimitiveIterator.OfInt it = s1.getDescendingIterator(0);
        assertFalse(it.hasNext());
        
        it = s1.getAscendingIterator(0);
    }
    
    
    @Ignore
    @Test
    public void testperf() {
        Runtime runtime = Runtime.getRuntime();
        System.out.println("allocated memory (KB): "+runtime.totalMemory()/1024);
        
        int n = 10000000;
        SortedIntArray sia = new SortedIntArray();
        long t0=System.currentTimeMillis();
        for(int i =0; i<n; i++) {
            sia.insert(i);
        }
        System.out.println("Populate sortedintarray: "+(System.currentTimeMillis()-t0)+" ms");
        System.out.println("allocated memory (KB): "+runtime.totalMemory()/1024);
        
        long t1=System.currentTimeMillis();
        Set<Integer> set= new HashSet<Integer>();
        for(int i=0;i<n;i++) {
            set.add(sia.get(i));
        }
        System.out.println("Populate hashset: "+(System.currentTimeMillis()-t1)+" ms");
        System.out.println("allocated memory (KB): "+runtime.totalMemory()/1024);
        
        for (int k=0; k<20; k++) {
            long t2=System.currentTimeMillis();
            long sum =0;
            for(int i=0;i<n;i++) {
                if(sia.search(i)>=0) {
                    sum+=i;
                }
            }
            System.out.println("sum: "+sum+" Search in sorted array: "+(System.currentTimeMillis()-t2)+" ms");
            System.out.println("allocated memory (KB): "+runtime.totalMemory()/1024);
            long t3=System.currentTimeMillis();
            sum =0;
            for(int i=0;i<n;i++) {
                if(set.contains(i)) {
                    sum+=i;
                }
            }
            System.out.println("sum: "+sum+" Search in hashset: "+(System.currentTimeMillis()-t3)+" ms");
            System.out.println("allocated memory (KB): "+runtime.totalMemory()/1024);
        }
    }
}
