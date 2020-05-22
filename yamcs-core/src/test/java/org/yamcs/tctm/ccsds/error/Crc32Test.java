package org.yamcs.tctm.ccsds.error;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

import org.junit.Test;

public class Crc32Test {
    
    @Test
    public void test1() {
        byte[] data = {0x12, 0x34, 0x56, 0x78};
        
      ProximityCrc32 c = new ProximityCrc32();
       assertEquals(0x34D74CB3, c.compute(data, 0, data.length));
    }
    
    @Test
    public void test2() {
        byte[] data = new byte[2048];
        Random random = new Random();
        random.nextBytes(data);
        ProximityCrc32 c = new ProximityCrc32();
        long t0 = System.nanoTime();
        int crc = 0;
        int n = 1000000;
        for(int i=0; i<n; i++) {
            crc = c.compute(data, 0, data.length);
        }
        data[0] = (byte)crc;
        long t1 = System.nanoTime();
        System.out.println("t1-t0 "+(t1-t0));
        System.out.println(crc+" speed "+(1.0*data.length*n*1000/(t1-t0)) +"MB/sec");
    }
    
    @Test
    public void test3() {
        byte[] data = new byte[2048];
        Random random = new Random();
        random.nextBytes(data);
        CRC32 crc32 = new CRC32();
        
        long t0 = System.nanoTime();
        long crc = 0;
        int n = 10000000;
        for(int i=0; i<n; i++) {
            crc32.update(data, 0, data.length);
            crc = crc32.getValue();
            data[0] = (byte)crc;
                 
        }
        long t1 = System.nanoTime();
        System.out.println("t1-t0 "+(t1-t0));
        System.out.println(crc+" speed "+(1.0*data.length*n*1000/(t1-t0)) +"MB/sec");
    }
    
    @Test
    public void test3b() {
        byte[] data = new byte[2048];
        ByteBuffer bb = ByteBuffer.allocateDirect(2048);
        Random random = new Random();
        random.nextBytes(data);
        bb.put(data);
        System.out.println("bb.class: "+bb.getClass());
        
        long t0 = System.nanoTime();
        long crc = 0;
        int n = 10000000;
        for(int i=0; i<n; i++) {
            CRC32 crc32 = new CRC32();
            bb.position(0);
            crc32.update(bb.duplicate());
            crc = crc32.getValue();
            data[0] = (byte)crc;
                 
        }
        long t1 = System.nanoTime();
        System.out.println("t1-t0 "+(t1-t0));
        System.out.println(crc+" speed "+(1.0*data.length*n*1000/(t1-t0)) +"MB/sec");
    }
    @Test
    public void test4() {
        byte[] data = new byte[2048];
        Random random = new Random();
        random.nextBytes(data);
        Adler32 adl32 = new Adler32();
        long t0 = System.nanoTime();
        long crc = 0;
        int n = 10000000;
        for(int i=0; i<n; i++) {
            adl32.update(data, 0, data.length);
            crc = adl32.getValue();
            data[0] = (byte)crc;
                 
        }
        long t1 = System.nanoTime();
        System.out.println("t1-t0 "+(t1-t0));
        System.out.println(crc+" speed "+(1.0*data.length*n*1000/(t1-t0)) +"MB/sec");
    }
}