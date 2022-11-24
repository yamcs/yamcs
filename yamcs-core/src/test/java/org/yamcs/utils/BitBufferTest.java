package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteOrder;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class BitBufferTest {

    @Test
    public void testBigEndianRead() {
        BitBuffer bitbuf = new BitBuffer(new byte[] { 0x18, 0x7A, 0x23, (byte) 0xFF }, 0);
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
    public void testBigEndianWrite() {
        byte[] x = new byte[10];
        BitBuffer bitbuf = new BitBuffer(x);
        bitbuf.putBits(0, 1);
        assertEquals(0, x[0]);

        bitbuf.putBits(1, 3);
        assertEquals(4, bitbuf.getPosition());

        assertEquals(0x10, x[0] & 0xFF);

        bitbuf.putBits(0xFF, 7);
        assertEquals(0x1F, x[0] & 0xFF);
        assertEquals(0xE0, x[1] & 0xFF);
    }

    @Test
    public void testBigEndianWrite1() {
        byte[] x = new byte[10];
        BitBuffer bitbuf = new BitBuffer(x);
        bitbuf.putBits(1, 1);
        assertEquals(0x80, x[0] & 0xFF);

        bitbuf.putBits(1, 3);
        assertEquals(4, bitbuf.getPosition());

        assertEquals(0x90, x[0] & 0xFF);
        bitbuf.setPosition(0);
        bitbuf.putBits(0, 1);
        assertEquals(0x10, x[0] & 0xFF);

        bitbuf.putBits(1, 1);
        assertEquals(0x50, x[0] & 0xFF);
        bitbuf.putBits(1, 1);
        assertEquals(0x70, x[0] & 0xFF);
        bitbuf.putBits(0, 1);
        assertEquals(0x60, x[0] & 0xFF);
        bitbuf.putBits(1, 2);
        assertEquals(0x64, x[0] & 0xFF);
    }

    @Test
    public void testBigEndianWrite2() {
        byte[] x = new byte[4];
        BitBuffer bitbuf = new BitBuffer(x);
        bitbuf.putBits(0x01020304, 32);
        assertEquals("01020304", StringConverter.arrayToHexString(x));
    }

    @Test
    public void testFastPut() {
        byte[] x = new byte[4];
        BitBuffer bitbuf = new BitBuffer(x);
        bitbuf.putByte((byte) 1);
        bitbuf.put(new byte[] { 2, 3, 4 });
        assertEquals("01020304", StringConverter.arrayToHexString(x));
    }

    @Test
    public void testBigEndianWrite3() {
        byte[] x = new byte[8];
        BitBuffer bitbuf = new BitBuffer(x);
        bitbuf.putBits(0x0102030405060708L, 64);
        assertEquals("0102030405060708", StringConverter.arrayToHexString(x));
    }

    @Test
    public void testBigEndianWrite4() {
        byte[] x = new byte[8];
        BitBuffer bitbuf = new BitBuffer(x);
        bitbuf.putBits(0x0102030405060708L, 60);
        assertEquals("1020304050607080", StringConverter.arrayToHexString(x));
    }

    @Test
    public void test2() {
        BitBuffer bitbuf = new BitBuffer(new byte[] { (byte) 0xE0, 0x7A }, 0);
        assertEquals(14, bitbuf.getBits(4));
    }

    @Test
    public void testLittleEndianRead() {
        BitBuffer bitbuf = new BitBuffer(new byte[] { 0x18, 0x7A, 0x23, (byte) 0xFF }, 0);
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
    public void testLittleEndianRead1() {
        BitBuffer bitbuf = new BitBuffer(new byte[] { 0x03, (byte) 0x80, (byte) 0xFF, (byte) 0xFF }, 0);
        bitbuf.setByteOrder(ByteOrder.LITTLE_ENDIAN);

        assertEquals(3, bitbuf.getBits(3));
        assertEquals(0, bitbuf.getBits(12));
        assertEquals(0x1FFFFL, bitbuf.getBits(17));
    }

    @Test
    public void testLittleEndianWrite1() {
        BitBuffer bitbuf = new BitBuffer(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00 });
        bitbuf.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        bitbuf.putBits(3, 3);
        bitbuf.putBits(0, 12);
        bitbuf.putBits(0x1FFFF, 17);

        assertEquals("0380FFFF", StringConverter.arrayToHexString(bitbuf.array()));
    }

    @Test
    public void testDoubleSlice() {
        BitBuffer bitbuf = new BitBuffer(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04 });
        assertEquals(1, bitbuf.getBits(8));

        BitBuffer bitbuf1 = bitbuf.slice();
        assertEquals(2, bitbuf1.getBits(8));

        BitBuffer bitbuf2 = bitbuf1.slice();
        assertEquals(3, bitbuf2.getBits(8));
    }

    // @Test
    public void testSpeed() {
        int n = 1000_000;
        byte[] b = new byte[n];
        BitBuffer bitbuf = new BitBuffer(b);

        long s = 0;
        Random r = new Random();
        long t0 = System.currentTimeMillis();

        long c = 0;

        for (int i = 0; i < 3000; i++) {
            bitbuf.setPosition(0);
            b[r.nextInt(n)] = (byte) r.nextInt();
            hopa: while (true) {
                for (int j = 1; j < 33; j++) {
                    if (bitbuf.getPosition() + 64 > n * 8) {
                        break hopa;
                    }
                    c++;
                    s += bitbuf.getBits(j);
                }
            }
        }
        long t1 = System.currentTimeMillis();
        System.out.println("s: " + s + " t1-t0: " + (t1 - t0) + " millisecs c: " + c);
    }

}
