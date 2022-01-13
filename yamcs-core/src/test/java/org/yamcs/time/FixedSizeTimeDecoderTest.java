package org.yamcs.time;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.yamcs.utils.StringConverter.*;

import java.nio.BufferUnderflowException;

public class FixedSizeTimeDecoderTest {

    @Test
    public void testDecode4bytes() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(4, 1000);
        long t = decoder.decode(hexStringToArray("01020304"), 0);
        assertEquals(1000l * 0x01020304, t);
    }

    @Test
    public void testDecodeRaw4bytes() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(4, 1000);
        long t = decoder.decodeRaw(hexStringToArray("01020304"), 0);
        assertEquals(0x01020304, t);
    }

    @Test
    public void testDecode8bytes() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(8, 0.1);
        long t = decoder.decode(hexStringToArray("0102030405060708"), 0);
        assertEquals((long) (0.1 * 0x0102030405060708l), t);
    }

    @Test
    public void testDecodeRaw8bytes() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(8, 0.1);
        long t = decoder.decodeRaw(hexStringToArray("0102030405060708"), 0);
        assertEquals(0x0102030405060708l, t);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSize() {
        new FixedSizeTimeDecoder(7, 1000);
    }

    @Test(expected = BufferUnderflowException.class)
    public void testBufferUnderflow() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(4, 1000);
        decoder.decodeRaw(hexStringToArray("0102030405060708"), 5);
    }
}
