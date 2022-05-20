package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.yamcs.utils.StringConverter.arrayToHexString;

import org.junit.jupiter.api.Test;

public class ByteArrayUtilsTest {
    @Test
    public void testPlusOne() {
        assertEquals("0102", plusOne("0101"));
        assertEquals("0200", plusOne("01FF"));
    }

    @Test
    public void testPlusOneOverflow() {
        assertThrows(IllegalArgumentException.class, () -> {
            plusOne("FFFF");
        });
    }

    @Test
    public void testMinusOne() {
        assertEquals("0102", minusOne("0103"));
        assertEquals("01FF", minusOne("0200"));
    }

    @Test
    public void testMinusOneUnderflow() {
        assertThrows(IllegalArgumentException.class, () -> {
            minusOne("0000");
        });
    }

    private String plusOne(String hex) {
        return arrayToHexString(ByteArrayUtils.plusOne(h2b(hex)));
    }

    private String minusOne(String hex) {
        return arrayToHexString(ByteArrayUtils.minusOne(h2b(hex)));
    }

    @Test
    public void testCompare() {
        assertEquals(0, ByteArrayUtils.compare(h2b("0001"), h2b("0001")));
        assertEquals(0, ByteArrayUtils.compare(h2b("0001"), h2b("000102")));
        assertEquals(0, ByteArrayUtils.compare(h2b("00010203"), h2b("000102")));
        assertEquals(-1, ByteArrayUtils.compare(h2b("0102"), h2b("0103")));
        assertEquals(1, ByteArrayUtils.compare(h2b("0103"), h2b("0102")));
    }

    @Test
    public void testLong() {
        byte[] a = h2b("0102030405060708");
        assertEquals(0x0102030405060708l, ByteArrayUtils.decodeLong(a, 0));

        byte[] b = new byte[8];
        ByteArrayUtils.encodeLong(0xF0F1F2F3F4F5F6F7l, b, 0);
        assertEquals("F0F1F2F3F4F5F6F7", arrayToHexString(b));

        assertEquals("0102030405060708",
                arrayToHexString(ByteArrayUtils.encodeLong(0x0102030405060708l)));
    }

    @Test
    public void testLongLE() {
        byte[] a = h2b("0102030405060708");
        assertEquals(0x0807060504030201l, ByteArrayUtils.decodeLongLE(a, 0));

        byte[] b = new byte[8];
        ByteArrayUtils.encodeLongLE(0xFFFEFDFCFBFAF0F9l, b, 0);
        assertEquals("F9F0FAFBFCFDFEFF", arrayToHexString(b));
    }

    @Test
    public void testUnsigned7Bytes() {
        byte[] b = h2b("F1F2F3F4F5F6F7");
        assertEquals(0xF1F2F3F4F5F6F7l, ByteArrayUtils.decodeUnsigned7Bytes(b, 0));
    }

    @Test
    public void testUnsigned6Bytes() {
        byte[] b = h2b("F1F2F3F4F5F6");
        assertEquals(0xF1F2F3F4F5F6l, ByteArrayUtils.decodeUnsigned6Bytes(b, 0));
    }

    @Test
    public void testSigned6Bytes() {
        byte[] b = h2b("FFFFFFFFFFFF");
        assertEquals(-1, ByteArrayUtils.decode6Bytes(b, 0));
        b = h2b("000000000102");
        assertEquals(0x0102, ByteArrayUtils.decode6Bytes(b, 0));

        ByteArrayUtils.encodeUnsigned6Bytes(0xFFFFFFFFFFFEl, b, 0);
        assertEquals("FFFFFFFFFFFE", arrayToHexString(b));

        ByteArrayUtils.encodeUnsigned6Bytes(0x010203040506l, b, 0);
        assertEquals("010203040506", arrayToHexString(b));
    }

    @Test
    public void testUnsigned5Bytes() {
        byte[] b = h2b("F1F2F3F4F5");
        assertEquals(0xF1F2F3F4F5l, ByteArrayUtils.decodeUnsigned5Bytes(b, 0));

        ByteArrayUtils.encodeUnsigned5Bytes(0xFFFFFFFFFEl, b, 0);
        assertEquals("FFFFFFFFFE", arrayToHexString(b));

        ByteArrayUtils.encodeUnsigned5Bytes(0x0102030405l, b, 0);
        assertEquals("0102030405", arrayToHexString(b));
    }

    @Test
    public void testSigned5Bytes() {
        byte[] b = h2b("FFFFFFFFFF");
        assertEquals(-1, ByteArrayUtils.decode5Bytes(b, 0));
        b = h2b("0000000102");
        assertEquals(0x0102, ByteArrayUtils.decode5Bytes(b, 0));

    }

    @Test
    public void testSignedInt() {
        byte[] b = h2b("FFFFFFFF");
        assertEquals(-1, ByteArrayUtils.decodeInt(b, 0));

        ByteArrayUtils.encodeInt(-2, b, 0);
        assertEquals("FFFFFFFE", arrayToHexString(b));

        assertEquals("FFFFFFFD", arrayToHexString(ByteArrayUtils.encodeInt(-3)));

    }

    @Test
    public void testSignedIntLE() {
        byte[] b = h2b("FEFFFFFF");
        assertEquals(-2, ByteArrayUtils.decodeIntLE(b, 0));
    }

    @Test
    public void testUnsigned3Bytes() {
        byte[] b = h2b("F1F2F3");
        assertEquals(0xF1F2F3l, ByteArrayUtils.decodeUnsigned3Bytes(b, 0));

        ByteArrayUtils.encodeUnsigned3Bytes(0xF1F2F3, b, 0);
        assertEquals("F1F2F3", arrayToHexString(b));

        ByteArrayUtils.encodeUnsigned3Bytes(0x010203, b, 0);
        assertEquals("010203", arrayToHexString(b));
    }

    @Test
    public void testUnsigned3BytesLE() {
        byte[] b = h2b("F1F2F3");
        assertEquals(0xF3F2F1l, ByteArrayUtils.decodeUnsigned3BytesLE(b, 0));
    }

    /*
     * @Test
     * public void testSignedShort() {
     * byte[] b = h2b("FFFF");
     * assertEquals(-1, ByteArrayUtils.decodeShort(b, 0));
     * 
     * b = h2b("0102");
     * assertEquals(0x0102, ByteArrayUtils.decodeShort(b, 0));
     * 
     * ByteArrayUtils.encodeShort(-2, b, 0);
     * assertEquals("FFFE", arrayToHexString(b));
     * 
     * ByteArrayUtils.encodeShort(2, b, 0);
     * assertEquals("0002", arrayToHexString(b));
     * 
     * }
     */
    @Test
    public void testUnsignedShort() {
        byte[] b = h2b("FFFF");
        assertEquals(0xFFFF, ByteArrayUtils.decodeUnsignedShort(b, 0));

        b = h2b("0102");
        assertEquals(0x0102, ByteArrayUtils.decodeShort(b, 0));

        ByteArrayUtils.encodeUnsignedShort(0xFFFE, b, 0);
        assertEquals("FFFE", arrayToHexString(b));

        ByteArrayUtils.encodeUnsignedShort(2, b, 0);
        assertEquals("0002", arrayToHexString(b));

    }

    @Test
    public void testUnsignedShortLE() {
        byte[] b = h2b("F1F2");
        assertEquals(0xF2F1, ByteArrayUtils.decodeUnsignedShortLE(b, 0));
    }

    // hex to binary
    private byte[] h2b(String hex) {
        return StringConverter.hexStringToArray(hex);
    }
}
