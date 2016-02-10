package org.yamcs.utils;

import org.junit.Test;
import org.yamcs.parameterarchive.BitBuffer;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

public class TestBitBuffer {
	
    @Test
    public void tesSingleBit1() {
    	ByteBuffer bb = ByteBuffer.allocate(16);
    	BitWriter bw =new BitWriter(bb);
        for(int i=0; i<128; i++) {
            bw.write(i, 1);
        }
        assertEquals(0x5555555555555555L, bb.getLong(0));
        bw.flush();
        bb.rewind();
        BitReader br = new BitReader(bb);
        for(int i=0; i<128; i++) {
            assertEquals(i&1, br.read(1));
        }
    }
    
    
    @Test
    public void tesVariableBits() {
    	ByteBuffer bb = ByteBuffer.allocate(32);
    	BitWriter bw  = new BitWriter(bb);
        
        for(int i=0; i<50; i++) {
            bw.write(1, 2);
            bw.write(3, 3);
        }
        bw.flush();
        
        assertEquals(0x5ad6b5ad6b5ad6b5L, bb.getLong(0));
        bb.rewind();
        BitReader br = new BitReader(bb);
        for(int i=0; i<50; i++) {
            assertEquals(1, br.read(2));
            assertEquals(3, br.read(3));
        }
    }
    
    @Test
    public void tesV32Bits() {
    	ByteBuffer bb = ByteBuffer.allocate(32);
    	BitWriter bw  = new BitWriter(bb);
    	bw.write(0x01020304, 32);
    	bw.flush();
    	
    	bb.rewind();
        BitReader br = new BitReader(bb);
        assertEquals(0x01020304, br.read(32));
    }
}
