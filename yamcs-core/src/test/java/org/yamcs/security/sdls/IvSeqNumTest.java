package org.yamcs.security.sdls;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IvSeqNumTest {

    @Test
    void testIncrementAndWraparound() {
        IvSeqNum seq = new IvSeqNum(1); // 1 byte = 0..255
        for (int i = 0; i < 255; i++) {
            seq.increment();
        }
        assertEquals("FF", seq.toString());

        seq.increment(); // should wrap to 00
        assertEquals("00", seq.toString());
    }

    @Test
    void testFastPathPlusOne() {
        IvSeqNum seq = new IvSeqNum(2); // 2 bytes
        IvSeqNum next = seq.clone();
        next.increment();
        assertTrue(seq.verifyInWindow(next, 5));
    }

    @Test
    void testWindowCheck() {
        IvSeqNum seq = new IvSeqNum(2);
        // seq = 0000
        IvSeqNum recv = seq.clone();
        recv.increment(); // 0001
        assertTrue(seq.verifyInWindow(recv, 10));

        recv.increment(); // 0002
        assertTrue(seq.verifyInWindow(recv, 10));

        IvSeqNum outside = seq.clone();
        for (int i = 0; i < 11; i++)
            outside.increment(); // 000B
        assertFalse(seq.verifyInWindow(outside, 10));
    }

    @Test
    void testWraparoundWindow() {
        IvSeqNum seq = new IvSeqNum(1); // 1 byte
        for (int i = 0; i < 250; i++)
            seq.increment(); // seq = FA
        IvSeqNum recv = seq.clone();
        for (int i = 0; i < 10; i++)
            recv.increment(); // wraps past FF -> 04
        assertTrue(seq.verifyInWindow(recv, 20));
        assertFalse(seq.verifyInWindow(recv, 5));
    }

    @Test
    void testEqualsAndCopy() {
        IvSeqNum a = new IvSeqNum(3);
        IvSeqNum b = a.clone();
        assertEquals(a, b);
        a.increment();
        assertNotEquals(a, b);
    }

    @Test
    void testFromBytesExactLength() {
        byte[] arr = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 4);
        assertEquals("01020304", iv.toString());
    }

    @Test
    void testFromBytesShorter() {
        byte[] arr = new byte[] { 0x0A, 0x0B };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 4);
        assertEquals("00000A0B", iv.toString());
    }

    @Test
    void testFromBytesLonger() {
        byte[] arr = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 4); // takes last 4 bytes
        assertEquals("02030405", iv.toString());
    }

    @Test
    void testFromBytesSingleOctet() {
        byte[] arr = new byte[] { 0x7F };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 1);
        assertEquals("7F", iv.toString());
    }

    @Test
    void testFromBytesPadding() {
        byte[] arr = new byte[] { 0x01 };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 3);
        assertEquals("000001", iv.toString());
    }

    @Test
    void testFromBytesExactLength12() {
        byte[] arr = new byte[] {
                0x01, 0x02, 0x03, 0x04,
                0x05, 0x06, 0x07, 0x08,
                0x09, 0x0A, 0x0B, 0x0C
        };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 12);
        assertEquals("0102030405060708090A0B0C", iv.toString());
    }

    @Test
    void testIncrementAndWraparound12() {
        IvSeqNum seq = new IvSeqNum(12);
        // set to max value manually
        byte[] maxBytes = new byte[12];
        for (int i = 0; i < 12; i++)
            maxBytes[i] = (byte) 0xFF;
        seq = IvSeqNum.fromBytes(maxBytes, 12);

        seq.increment();
        // wraps to zero
        assertEquals("000000000000000000000000", seq.toString());
    }

    @Test
    void testFastPathPlusOne12() {
        IvSeqNum seq = new IvSeqNum(12);
        seq.increment(); // 000...001
        IvSeqNum next = seq.clone();
        next.increment(); // 000...002
        assertTrue(seq.verifyInWindow(next, 10));
    }

    @Test
    void testWindowCheck12() {
        IvSeqNum seq = new IvSeqNum(12);
        IvSeqNum recv = seq.clone();
        recv.increment();
        assertTrue(seq.verifyInWindow(recv, 5));

        // increment 6 times, outside window of 5
        for (int i = 0; i < 6; i++)
            recv.increment();
        assertFalse(seq.verifyInWindow(recv, 5));
    }

    @Test
    void testWraparoundWindow12() {
        IvSeqNum seq = new IvSeqNum(12);
        // set seq near wraparound (FF..FE)
        byte[] nearMax = new byte[12];
        for (int i = 0; i < 12; i++)
            nearMax[i] = (byte) 0xFF;
        nearMax[11] = (byte) 0xFE; // least significant byte = FE
        seq = IvSeqNum.fromBytes(nearMax, 12);

        IvSeqNum recv = seq.clone();
        recv.increment(); // wrap to FF..FF
        assertTrue(seq.verifyInWindow(recv, 2));

        recv.increment(); // wrap increment to 00..00
        assertTrue(seq.verifyInWindow(recv, 2));

        recv.increment(); // increment 00..01
        assertFalse(seq.verifyInWindow(recv, 2));
    }

    @Test
    void testCopyAndEquals12() {
        IvSeqNum a = new IvSeqNum(12);
        IvSeqNum b = a.clone();
        assertEquals(a, b);
        a.increment();
        assertNotEquals(a, b);
    }

    @Test
    void testToBytesExactLength() {
        byte[] arr = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 12);
        byte[] bytes = iv.toBytes(12);
        assertArrayEquals(arr, bytes);
    }

    @Test
    void testToBytesWithPadding() {
        byte[] arr = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 4);
        byte[] bytes = iv.toBytes(6); // pad to 6 bytes
        byte[] expected = new byte[] { 0x00, 0x00, 0x01, 0x02, 0x03, 0x04 };
        assertArrayEquals(expected, bytes);
    }

    @Test
    void testFromBytesToBytesReversible() {
        byte[] arr = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C };
        IvSeqNum iv = IvSeqNum.fromBytes(arr, 12);
        byte[] out = iv.toBytes(12);
        assertArrayEquals(arr, out);
    }
}