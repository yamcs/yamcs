package org.yamcs.utils;

import java.nio.ByteBuffer;

/**
 * Wrapper around a ByteBuffer that allows to write individual bits or group of bits
 * 
 * All the writings/readings are performed to a temporary long  which is stored into the backing ByteBuffer when it is "full"
 * 
 * Don't forget to call flush after the last write, such that the long is written to the backing ByteBuffer
 *  
 * @author nm
 *
 */
public class BitWriter {
    
    private int bitShift; //bit offset from the right inside the current int
    final private ByteBuffer bb;
    
    //we put all the bits in the b, when it is full we save it in the array a and increase the offset
    private long b; //current element
    
    /**
     * Constructs a BitWriter around an existing ByteBuffer
     *
     * @param bb
     */
    public BitWriter(ByteBuffer bb) {
        this.bb = bb;
        bitShift = 64;
        b = 0;
    }
  

    /**
     * write the least significant numBits of x into the BitBuffer
     * 
     * Note that there is no check that the bits will actually fit into the ByteBuffer, they will be stored in the temporary field. 
     *  A buffer overflow exception will happen when the temporary field is full and flushed to the buffer 
     * 
     * @param x
     * @param numBits
     */
    public void write(int x, int numBits) {
        int k = numBits-bitShift;
        if(k<0) {
            doWrite(x, numBits);
        } else {
            doWrite(x>>k, bitShift);
            if(k>0) {
            	bitShift = 64;
            	bb.putLong(b);
            	b = 0;
            	doWrite(x, k);
            }
        }
      //  System.out.println("bitShift: "+bitShift+" bb.position: "+bb.position()+" numBits: "+numBits);
    }
    
    //here we know that numBits<bitShift
    private void doWrite(int x, int numBits) {     
        bitShift-=numBits;
        long mask = (1L<<numBits) -1;
        b |= ((x&mask) << bitShift);
    }
    

    /**
     * flush the temporary long to the ByteBuffer
     * do not call this method twice!!
     */
    public void flush() {
    	if(bitShift!=64) {
    		bb.putLong(b);
    	}
    }
}
