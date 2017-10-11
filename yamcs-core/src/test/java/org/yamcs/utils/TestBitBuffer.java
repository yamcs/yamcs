package org.yamcs.utils;

import static org.junit.Assert.*;

import java.nio.ByteOrder;

import org.junit.Test;

public class TestBitBuffer {
    @Test
    public void testBigEndian() {
        BitBuffer bitbuf = new BitBuffer(new byte[]{0x18, 0x7A, 0x23, (byte) 0xFF},0);
        assertEquals(0, bitbuf.getBits(1));
        assertEquals(1, bitbuf.getPosition());
        assertEquals(1, bitbuf.getBits(3));
        assertEquals(4, bitbuf.getPosition());
        
        bitbuf.setPosition(0);        
        assertEquals(0x18, bitbuf.getBits(8));
        
        bitbuf.setPosition(4);
        assertEquals(0x87, bitbuf.getBits(8));
        
        bitbuf.setPosition(0);        
        assertEquals(0x187A, bitbuf.getBits(16));
        
        bitbuf.setPosition(4);
        assertEquals(0x87A, bitbuf.getBits(12));
        
        bitbuf.setPosition(4);
        assertEquals(0x87A2, bitbuf.getBits(16));
        
        bitbuf.setPosition(4);
        assertEquals(0x87A23, bitbuf.getBits(20));
        
        bitbuf.setPosition(0);
        
        assertEquals(0x187A23FF, bitbuf.getBits(32));
    }
    
    @Test
    public void test2() {
        BitBuffer bitbuf = new BitBuffer(new byte[]{(byte)0xE0, 0x7A}, 0);
        assertEquals(14, bitbuf.getBits(4));
    }
    
    @Test
    public void testLittleEndian() {
        BitBuffer bitbuf = new BitBuffer(new byte[]{0x18, 0x7A, 0x23, (byte) 0xFF},0);
        bitbuf.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        
        assertEquals(0, bitbuf.getBits(1));
        assertEquals(1, bitbuf.getPosition());

        assertEquals(4, bitbuf.getBits(3));
        assertEquals(4, bitbuf.getPosition());
        
        bitbuf.setPosition(0);        
        assertEquals(0x18, bitbuf.getBits(8));
        
        bitbuf.setPosition(4);
        assertEquals(0xA1, bitbuf.getBits(8));
        
        bitbuf.setPosition(0);        
        assertEquals(0x7A18, bitbuf.getBits(16));
        
        bitbuf.setPosition(4);
        assertEquals(0x7A1, bitbuf.getBits(12));
        
        bitbuf.setPosition(4);
        assertEquals(0x37A1, bitbuf.getBits(16));
        
        bitbuf.setPosition(4);
        assertEquals(0x237A1, bitbuf.getBits(20));
        
        bitbuf.setPosition(0);
        
        assertEquals(0xFF237A18L, bitbuf.getBits(32));
    }
    
    @Test
    public void testLittleEndian1() {
        BitBuffer bitbuf = new BitBuffer(new byte[]{0x03, (byte)0x80, (byte)0xFF, (byte) 0xFF}, 0);
        bitbuf.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        
        assertEquals(3, bitbuf.getBits(3));        
        assertEquals(0, bitbuf.getBits(12));
        assertEquals(0x1FFFFL, bitbuf.getBits(17));
    }
}
