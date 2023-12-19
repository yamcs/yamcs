package org.yamcs.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.yamcs.utils.StringConverter.hexStringToArray;

import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

public class FixedSizeTimeDecoderTest {

    @Test
    public void testDecode4bytes() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(ByteOrder.BIG_ENDIAN, 4, 1000);
        long t = decoder.decode(hexStringToArray("01020304"), 0);
        assertEquals(1000l * 0x01020304, t);
    }

    @Test
    public void testDecodeRaw4bytes() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(ByteOrder.BIG_ENDIAN, 4, 1000);
        long t = decoder.decodeRaw(hexStringToArray("01020304"), 0);
        assertEquals(0x01020304, t);
    }

    @Test
    public void testDecode8bytes() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(ByteOrder.BIG_ENDIAN, 8, 0.1);
        long t = decoder.decode(hexStringToArray("0102030405060708"), 0);
        assertEquals((long) (0.1 * 0x0102030405060708l), t);
    }

    @Test
    public void testDecodeRaw8bytes() {
        FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(ByteOrder.BIG_ENDIAN, 8, 0.1);
        long t = decoder.decodeRaw(hexStringToArray("0102030405060708"), 0);
        assertEquals(0x0102030405060708l, t);
    }

    @Test
    public void testInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FixedSizeTimeDecoder(ByteOrder.BIG_ENDIAN, 7, 1000);
        });
    }

    @Test
    public void testBufferUnderflow() {
        assertThrows(BufferUnderflowException.class, () -> {
            FixedSizeTimeDecoder decoder = new FixedSizeTimeDecoder(ByteOrder.BIG_ENDIAN, 4, 1000);
            decoder.decodeRaw(hexStringToArray("0102030405060708"), 5);
        });
    }
}
