package org.yamcs.utils;

import java.nio.ByteBuffer;

/**
 * BitReader is a wrapper around the ByteBuffer that allows to read individual bits.
 * 
 * All the writings/readings are performed from a temporary long;
 *  
 * @author nm
 *
 */
public class BitReader {
    private int bitShift; //bit offset from the right inside the b
    private long b; //current element
    final private ByteBuffer bb;
    
    /**
     * Constructs a buffer of size 8*n backed by an long[n] array. You need to allocate one bit more than required to store the data
     *
     * @param n
     */
    public BitReader(ByteBuffer bb) {
        this.bb = bb;
        bitShift = 64;
        b = bb.getLong();
    }
  

    public long readLong(int numBits) {
        int k = numBits-bitShift;
        if(k<0) {
            return doRead(numBits);
        } else {
            long x= doRead(bitShift)<<k;
            if(k>0) {
            	bitShift = 64;
            	b = bb.getLong();
            	x|=doRead(k);
            }
            return x;
        }
    }
    
    public int read(int numBits) {
        return (int)readLong(numBits);
    }
    
    private long doRead(int numBits) {
        bitShift-=numBits;
        long mask = (1L<<numBits) -1;
        return (b>>bitShift)&mask;
    }
}
