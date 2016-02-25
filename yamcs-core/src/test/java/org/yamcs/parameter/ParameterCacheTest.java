package org.yamcs.parameter;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;

public class ParameterCacheTest {
    Parameter p1 = new Parameter("p1");
    Parameter p2 = new Parameter("p2");
    
    @Test
    public void test1() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 1000, 4096);
       
        ParameterCache pcache = new ParameterCache(pcc); //1 second
        assertNull(pcache.getLastValue(p1));
        
        ParameterValue p1v1 = getParameterValue(p1, 10);
        ParameterValue p2v1 = getParameterValue(p2, 10);
        pcache.update(Arrays.asList(p1v1, p2v1));
        
        assertEquals(p1v1, pcache.getLastValue(p1));
        assertEquals(p2v1, pcache.getLastValue(p2));
        
        
        ParameterValue p1v2 = getParameterValue(p1, 20);
        pcache.update(Arrays.asList(p1v2));
        
        assertEquals(p1v2, pcache.getLastValue(p1));
        assertEquals(p2v1, pcache.getLastValue(p2));
        
        
        List<ParameterValue> pvlist = pcache.getValues(Arrays.asList(p1, p2));
        checkEquals(pvlist, p1v2, p2v1);
        
        pvlist = pcache.getValues(Arrays.asList(p2, p1));
        checkEquals(pvlist, p2v1, p1v1);
        
    }
    
    @Test
    public void testCircularity() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 1000, 4096);
        ParameterCache pcache = new ParameterCache(pcc); //1 second
        assertNull(pcache.getLastValue(p1));
        List<ParameterValue> expectedPVlist = new ArrayList<>();
        for(int i=0;i<10;i++) {
            ParameterValue pv = getUint64ParameterValue(p1, i*10L);
            expectedPVlist.add(pv);
            pcache.update(Arrays.asList(pv));
        }
        
        List<ParameterValue> pvlist = pcache.getAllValues(p1);
        assertEquals(10, pvlist.size());
        for(int i=0; i<10; i++) {
            assertEquals(expectedPVlist.get(9-i), pvlist.get(i));
        }
        
        for(int i=10;i<128;i++) {
            ParameterValue pv = getUint64ParameterValue(p1, i*10L);
            expectedPVlist.add(pv);
            pcache.update(Arrays.asList(pv));
        }
        
        pvlist = pcache.getAllValues(p1);
        
        assertEquals(128, pvlist.size());
        for(int i=0; i<128; i++) {
            assertEquals(expectedPVlist.get(127-i), pvlist.get(i));
        }
        
        ParameterValue pv = getUint64ParameterValue(p1, 128*10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);
        
        pvlist = pcache.getAllValues(p1);
        
        assertEquals(128, pvlist.size());
        for(int i=0; i<128; i++) {
            assertEquals(expectedPVlist.get(128-i), pvlist.get(i));
        }
    }
    
    @Test
    public void testResize() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 2000, 4096);
        ParameterCache pcache = new ParameterCache(pcc); //should keep at least 200 samples
        assertNull(pcache.getLastValue(p1));
        List<ParameterValue> expectedPVlist = new ArrayList<>();
        for(int i=0;i<256;i++) {
            ParameterValue pv = getUint64ParameterValue(p1, i*10L);
            expectedPVlist.add(pv);
            pcache.update(Arrays.asList(pv));
        }
        
        List<ParameterValue> pvlist = pcache.getAllValues(p1);
        assertEquals(256, pvlist.size());
        for(int i=0; i<256; i++) {
            assertEquals(expectedPVlist.get(255-i), pvlist.get(i));
        }
        ParameterValue pv = getUint64ParameterValue(p1, 256*10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);
        
        pv = getUint64ParameterValue(p1, 257*10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);
        
        pvlist = pcache.getAllValues(p1);
        assertEquals(256, pvlist.size());
        for(int i=0; i<256; i++) {
            assertEquals(expectedPVlist.get(257-i), pvlist.get(i));
        }
        
    }
    
    @Test
    public void testMaxSize() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 2000, 128);
        ParameterCache pcache = new ParameterCache(pcc); //should keep max 128 samples
        assertNull(pcache.getLastValue(p1));
        List<ParameterValue> expectedPVlist = new ArrayList<>();
        for(int i=0;i<256;i++) {
            ParameterValue pv = getUint64ParameterValue(p1, i*10L);
            expectedPVlist.add(pv);
            pcache.update(Arrays.asList(pv));
        }
        
        List<ParameterValue> pvlist = pcache.getAllValues(p1);
        assertEquals(128, pvlist.size());
        for(int i=0; i<128; i++) {
            assertEquals(expectedPVlist.get(255-i), pvlist.get(i));
        }
        ParameterValue pv = getUint64ParameterValue(p1, 256*10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);
        
        pv = getUint64ParameterValue(p1, 257*10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);
        
        pvlist = pcache.getAllValues(p1);
        assertEquals(128, pvlist.size());
        for(int i=0; i<128; i++) {
            assertEquals(expectedPVlist.get(257-i), pvlist.get(i));
        }
        
    }
    
    /**
     * 
     * Perforamnce tests for different syncrhonization strategies in ParameterCache.CacheEntry
     * 
     * Results on Quad Core Intel(R) Core(TM) i7-4610M CPU @ 3.00GHz
     * Ubuntu 14.04
     * java version "1.8.0_66"
     * Java(TM) SE Runtime Environment (build 1.8.0_66-b17)
     * Java HotSpot(TM) 64-Bit Server VM (build 25.66-b17, mixed mode)

     *  
     *  1. synchronised CacheEntry.getAll and CacheEntry.add
     *  
     *    writer time 42.470 s
     *    totalReadTime: 334.741
     *  
     *  
     *  2. Synchronised CacheEntry.add and unsynchronised CacheEntry.getAll with volatile elements and tail
     *     writer time: 14.603 ms
     *     totalReadTime: 110.966 ms
     *  
     *  
     * 3. writeLock in CacheEntry.add and readLock in CacheEntry.getAll
     *     writer time: 19.329 ms
     *     totalReadTime: 125.052 ms
     * 
     * Due to these results, and the fact that locking offers the advantage that getAll gives correctly sorted results, 
     * we have selected the read/write lock implementation
     * 
     * 
     */
    
    static int numWrites = 100000;
    static int numReads = 1000000;
    static int numParam = 300;
    static int numReaders = 10;
    @Test
    @Ignore
    public void testConcurrency() throws InterruptedException {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 1000, 4096);
        final ParameterCache pcache = new ParameterCache(pcc);
        Thread writer = new Thread(new Runnable() {
            @Override
            public void run() {
                long t0 = System.currentTimeMillis();
                List<Parameter> plist = new ArrayList<Parameter>();
                for(int i=2;i<numParam; i++) {
                    plist.add(new Parameter("p"+i));
                }
                
                for(long t=0; t<numWrites*10; t+=10) {
                    ArrayList<ParameterValue> pvList = new ArrayList<ParameterValue>();
                    pvList.add(getUint64ParameterValue(p1,t));
                    for (int i=2; i<numParam; i++) {
                        pvList.add(getUint64ParameterValue(plist.get(i-2),t));
                    }
                    pcache.update(pvList);
                }
                long t1 = System.currentTimeMillis();
                System.out.println("writer finished in "+(t1-t0)+" ms");
            }
        });
        
        writer.start();
        Thread.sleep(100);
        CacheReader[] readers = new CacheReader[numReaders];
        for(int i=0; i<numReaders;i++) {
            readers[i] = new CacheReader(i, pcache, p1);
            new Thread(readers[i]).start();
        }
        writer.join();
        long totalReadTime =0;
        for(CacheReader r:readers) {
            totalReadTime+=r.runningTime;
        }
        System.out.println("totalReadTime: "+totalReadTime);
        
    }
    static class CacheReader implements Runnable {
        ParameterCache pcache;
        Parameter p1;
        int x;
        long runningTime;
        CacheReader(int x, ParameterCache pcache, Parameter p1) {
            this.p1 = p1;
            this.pcache = pcache;
            this.x = x;
        }
        
        @Override
        public void run() {
            long t0 = System.currentTimeMillis();
            int wc =0;
            for(long t=0; t<numReads*10; t+=10) {
                List<ParameterValue> pvlist = pcache.getAllValues(p1);
               // System.out.println("reader received "+pvlist);
                if(pvlist!=null)  {
                   // System.out.println("reader received: "+pvlist.size());
                    ParameterValue pv = pvlist.get(0);
                    for(int i=1;i<pvlist.size();i++) {
                        ParameterValue pv1 = pvlist.get(i);
                        long pvt = pv.getAcquisitionTime();
                        long pv1t = pv1.getAcquisitionTime();
                        
                        if(pv1t>pvt) {
                            wc++;
                          //  System.out.println("reader "+x+" "+t+"  wrong order: pv: "+pvt+" pv1: "+pv1t+" diff: "+(pv1t-pvt)/10);
                        }
                        pv = pv1;
                    }
                }
            }
            long t1 = System.currentTimeMillis();
            runningTime = t1-t0;
            System.out.println("reader "+x+" finished in "+runningTime+" ms, wrong order count: "+wc);
        }
    }
    
    ParameterValue getUint64ParameterValue(Parameter p, long t) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(t);
        pv.setEngineeringValue(ValueUtility.getUint64Value(t));
        return pv;
    }
    ParameterValue getParameterValue(Parameter p, long timestamp) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(timestamp);
        pv.setEngineeringValue(ValueUtility.getStringValue(p.getName()+"_"+timestamp));
        return pv;
    }
    
    public static void checkEquals(List<ParameterValue> actual, ParameterValue... expected) {
        assertEquals(expected.length, actual.size());
        for(int i=0; i<expected.length; i++) {
            ParameterValue pv = expected[i];
            assertEquals(pv, actual.get(i));
        }
    }
} 
