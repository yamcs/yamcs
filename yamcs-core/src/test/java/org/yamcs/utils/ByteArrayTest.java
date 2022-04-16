package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

public class ByteArrayTest {
    static Random rand = new Random();

    @Test
    public void testString0() throws DecodingException {
        ByteArray ba = new ByteArray();
        ba.addNullTerminatedUTF("aa");
        assertEquals(3, ba.size());
        assertEquals("aa", ba.getNullTerminatedUTF());

        assertEquals(3, ba.position());
    }

    @Test
    public void testString1() throws DecodingException {
        String s1 = "abc";
        String s2 = "cdh";

        ByteArray ba = new ByteArray();
        ba.addSizePrefixedUTF(s1);
        ba.addNullTerminatedUTF(s2);
        ba.add((byte) 42);

        String s1out = ba.getSizePrefixedUTF();
        String s2out = ba.getNullTerminatedUTF();

        assertEquals(s1, s1out);
        assertEquals(s2, s2out);
        assertEquals(42, ba.get());
    }

    @Test
    public void testString2() throws DecodingException {
        byte[] x = new byte[100];
        rand.nextBytes(x);
        String s1 = new String(x);

        ByteArray ba = new ByteArray();
        ba.addSizePrefixedUTF(s1);

        String s2 = ba.getSizePrefixedUTF();
        assertEquals(s1, s2);
    }

    @Test
    public void testString3() throws DecodingException {
        byte[] x = new byte[100];
        rand.nextBytes(x);
        String s1 = new String(x);

        ByteArray ba = new ByteArray();
        ba.addNullTerminatedUTF(s1);

        String s2 = ba.getNullTerminatedUTF();
        assertEquals(s1, s2);
    }
}
