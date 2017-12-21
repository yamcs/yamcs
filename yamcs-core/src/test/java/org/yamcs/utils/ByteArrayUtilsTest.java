package org.yamcs.utils;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Test;

public class ByteArrayUtilsTest {
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
