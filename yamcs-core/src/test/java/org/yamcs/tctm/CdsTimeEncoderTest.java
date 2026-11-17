package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.yamcs.tctm.ccsds.time.CdsTimeDecoder;
import org.yamcs.tctm.ccsds.time.CdsTimeEncoder;
import org.yamcs.utils.StringConverter;

/**
 * Same test vector as in {@link CdsTimeDecoderTest}: 2018-07-06T11:41:18.284Z is day 22101 (0x5655) since 1958-01-01
 * and millisecond of day 42078284 (0x0282104C), so 22101 * 86400000 + 42078284 = 1909568478284 ms since the epoch.
 */
public class CdsTimeEncoderTest {
    static final long T = 1_909_568_478_284L;

    static String encode(CdsTimeEncoder cte, long t) {
        byte[] b = new byte[cte.getEncodedLength()];
        int n = cte.encode(t, b, 0);
        assertEquals(b.length, n);
        return StringConverter.arrayToHexString(b);
    }

    static String encodeRaw(CdsTimeEncoder cte, long t) {
        byte[] b = new byte[cte.getEncodedLength()];
        int n = cte.encodeRaw(t, b, 0);
        assertEquals(b.length, n);
        return StringConverter.arrayToHexString(b);
    }

    @Test
    public void testImplicit() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x40, true);
        assertEquals(6, cte.getEncodedLength());
        assertEquals("56550282104C", encode(cte, T));
    }

    @Test
    public void testExplicit() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x40, false);
        assertEquals(7, cte.getEncodedLength());
        assertEquals("4056550282104C", encode(cte, T));
    }

    @Test
    public void test24BitDay() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x44, false);
        assertEquals("440056550282104C", encode(cte, T));
    }

    @Test
    public void testMicroseconds() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x41, true);
        // the microsecond segment is 0 when encoding milliseconds
        assertEquals("56550282104C0000", encode(cte, T));
        // raw time is in microseconds: 789 us = 0x0315
        assertEquals("56550282104C0315", encodeRaw(cte, T * 1000 + 789));
    }

    @Test
    public void testPicoseconds() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x42, true);
        assertEquals("56550282104C00000000", encode(cte, T));
        // 789 us = 789000000 ps = 0x2F072F40
        assertEquals("56550282104C2F072F40", encodeRaw(cte, T * 1000 + 789));
    }

    @Test
    public void testRoundTrip() {
        for (int pfield : new int[] { 0x40, 0x41, 0x42, 0x44, 0x45, 0x46, 0x48 }) {
            CdsTimeEncoder cte = new CdsTimeEncoder(pfield, false);
            CdsTimeDecoder ctd = new CdsTimeDecoder(-1);
            byte[] b = new byte[cte.getEncodedLength()];
            cte.encode(T, b, 0);
            assertEquals(T, ctd.decode(b, 0));

            long raw = T * 1000 + 123;
            cte.encodeRaw(raw, b, 0);
            assertEquals(pfield == 0x40 || pfield == 0x44 || pfield == 0x48 ? T * 1000 : raw, ctd.decodeRaw(b, 0));
        }
    }

    @Test
    public void testOutOfRange() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x40, true);
        byte[] b = new byte[6];
        // the 16 bit day segment holds days 0 to 65535, so day 65536 (65536 * 86400000 ms) does not fit
        assertThrows(IllegalArgumentException.class, () -> cte.encode(5_662_310_400_000L, b, 0));
        assertEquals(6, cte.encode(5_662_310_400_000L - 1, b, 0));
        // last millisecond of day 65535: 86399999 = 0x05265BFF
        assertEquals("FFFF05265BFF", StringConverter.arrayToHexString(b));

        assertThrows(IllegalArgumentException.class, () -> cte.encode(-1, b, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdsTimeEncoder(0x2E, true));
    }
}
