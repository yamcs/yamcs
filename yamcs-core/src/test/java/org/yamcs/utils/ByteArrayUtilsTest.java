package org.yamcs.utils;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Test;

public class ByteArrayUtilsTest {
    
    @Test
    public void testLongBELE() {
        byte[] a = StringConverter.hexStringToArray("0102030405060708");
        assertEquals(0x0102030405060708l, ByteArrayUtils.decodeLong(a, 0));
        assertEquals(0x0807060504030201l, ByteArrayUtils.decodeLongLE(a, 0));
        
        byte[] b = new byte[8];
        ByteArrayUtils.encodeLong(0xF0F1F2F3F4F5F6F7l, b, 0);
        assertEquals("F0F1F2F3F4F5F6F7", StringConverter.arrayToHexString(b));
        
        ByteArrayUtils.encodeLongLE(0xFFFEFDFCFBFAF0F9l, b, 0);
        assertEquals("F9F0FAFBFCFDFEFF", StringConverter.arrayToHexString(b));
    }
    
    
    @Test
    public void testUnsignedIntDecode() {
        byte[] b = new byte[8];
        long l = 0xFE010203L;
        ByteBuffer.wrap(b).putLong(l);
        long l1 = Integer.toUnsignedLong(ByteArrayUtils.decodeInt(b, 4));
        assertEquals(l, l1);
    }
    
    @Test
    public void testUnsignedIntEncode() {
        byte[] b = new byte[8];
        long l = 0xEA010203L;
        ByteArrayUtils.encodeInt((int)l, b, 4);
        long l1 = ByteBuffer.wrap(b).getLong();
        assertEquals(l, l1);
    }
}
