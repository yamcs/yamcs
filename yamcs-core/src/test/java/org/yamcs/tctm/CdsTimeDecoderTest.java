package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.yamcs.tctm.ccsds.time.CdsTimeDecoder;
import org.yamcs.utils.ByteSupplier;
import org.yamcs.utils.StringConverter;

/**
 * The test vectors use the time 2018-07-06T11:41:18.284Z with the CCSDS epoch 1958-01-01.
 * <p>
 * Day segment: from 1958-01-01 to 1970-01-01 there are 12 * 365 + 3 (1960, 1964, 1968) = 4383 days. The Unix time is
 * 1530877278284 ms = 17718 days * 86400000 + 42078284 ms. So the day is 4383 + 17718 = 22101 = 0x5655.
 * <p>
 * Millisecond of day: 42078284 = 11h * 3600000 + 41m * 60000 + 18284 = 0x0282104C.
 */
public class CdsTimeDecoderTest {
    // 4383 days * 86400000 ms
    static final long MILLIS_1958_TO_1970 = 378_691_200_000L;
    static final String EXPECTED = "2018-07-06T11:41:18.284Z";

    static String toUtc(long millisSince1958) {
        return Instant.ofEpochMilli(millisSince1958 - MILLIS_1958_TO_1970).toString();
    }

    @Test
    public void testImplicit16BitDay() {
        // pfield 0x40 = 0 100 0 0 00: CDS, 1958 epoch, 16 bit day, no submillisecond
        CdsTimeDecoder ctd = new CdsTimeDecoder(0x40);
        byte[] b = StringConverter.hexStringToArray("56550282104C");
        long t = ctd.decode(b, 0);
        // 22101 * 86400000 + 42078284
        assertEquals(1_909_568_478_284L, t);
        assertEquals(EXPECTED, toUtc(t));
    }

    @Test
    public void testExplicit() {
        CdsTimeDecoder ctd = new CdsTimeDecoder(-1);
        byte[] b = StringConverter.hexStringToArray("4056550282104C");
        assertEquals(EXPECTED, toUtc(ctd.decode(b, 0)));
        // raw is in microseconds
        assertEquals(1_909_568_478_284_000L, ctd.decodeRaw(b, 0));
    }

    @Test
    public void test24BitDay() {
        // pfield 0x44 = 0 100 0 1 00: 24 bit day, day 22101 = 0x005655
        CdsTimeDecoder ctd = new CdsTimeDecoder(-1);
        byte[] b = StringConverter.hexStringToArray("440056550282104C");
        assertEquals(EXPECTED, toUtc(ctd.decode(b, 0)));
    }

    @Test
    public void testMicroseconds() {
        // pfield 0x41 = 0 100 0 0 01: 16 bit microsecond of millisecond; 789 us = 0x0315
        CdsTimeDecoder ctd = new CdsTimeDecoder(0x41);
        byte[] b = StringConverter.hexStringToArray("56550282104C0315");
        // the submillisecond part is dropped (not rounded)
        assertEquals(EXPECTED, toUtc(ctd.decode(b, 0)));
        assertEquals(1_909_568_478_284_789L, ctd.decodeRaw(b, 0));
    }

    @Test
    public void testPicoseconds() {
        // pfield 0x42 = 0 100 0 0 10: 32 bit picosecond of millisecond; 789123456 ps = 0x2F091180 = 789 us
        CdsTimeDecoder ctd = new CdsTimeDecoder(0x42);
        byte[] b = StringConverter.hexStringToArray("56550282104C2F091180");
        assertEquals(EXPECTED, toUtc(ctd.decode(b, 0)));
        assertEquals(1_909_568_478_284_789L, ctd.decodeRaw(b, 0));
    }

    @Test
    public void testAgencyEpoch() {
        // pfield 0x48 = 0 100 1 0 00: agency defined epoch; the epoch bit does not change the decoding
        CdsTimeDecoder ctd = new CdsTimeDecoder(-1);
        byte[] b = StringConverter.hexStringToArray("4800010000000A");
        // 1 day + 10 ms
        assertEquals(86_400_010L, ctd.decode(b, 0));
    }

    @Test
    public void testLeapSecond() {
        // 2016-12-31T23:59:60.500Z: day 4383 + 17166 = 21549 = 0x542D, ms of day 86400500 = 0x05265DF4
        CdsTimeDecoder ctd = new CdsTimeDecoder(0x40);
        byte[] b = StringConverter.hexStringToArray("542D05265DF4");
        assertEquals(new CdsTimeDecoder.DayTime(21549, 86_400_500), ctd.decodeDayTime(b, 0));
        // a count of 86400000 ms days has no place for the leap second, so decode() gives the same value as for
        // the first second of the following day (the packet preprocessor uses decodeDayTime instead)
        assertEquals("2017-01-01T00:00:00.500Z", toUtc(ctd.decode(b, 0)));

        // a free running on-board clock has no leap seconds, so the raw time refuses them
        byte[] b1 = b;
        assertThrows(IllegalArgumentException.class, () -> ctd.decodeRaw(b1, 0));

        // the last millisecond of a leap second, 86400999 = 0x05265FE7, is the largest valid value
        b = StringConverter.hexStringToArray("542D05265FE7");
        assertEquals(new CdsTimeDecoder.DayTime(21549, 86_400_999), ctd.decodeDayTime(b, 0));
    }

    @Test
    public void testInvalidSubmillisecond() {
        // 999 = 0x03E7 microseconds is the maximum, 1000 = 0x03E8 is invalid
        CdsTimeDecoder us = new CdsTimeDecoder(0x41);
        assertEquals(1_909_568_478_284_999L, us.decodeRaw(StringConverter.hexStringToArray("56550282104C03E7"), 0));
        byte[] b = StringConverter.hexStringToArray("56550282104C03E8");
        assertThrows(IllegalArgumentException.class, () -> us.decodeRaw(b, 0));
        assertThrows(IllegalArgumentException.class, () -> us.decodeDayTime(b, 0));

        // 999999999 = 0x3B9AC9FF picoseconds is the maximum, 1000000000 = 0x3B9ACA00 is invalid
        CdsTimeDecoder ps = new CdsTimeDecoder(0x42);
        assertEquals(1_909_568_478_284_999L,
                ps.decodeRaw(StringConverter.hexStringToArray("56550282104C3B9AC9FF"), 0));
        byte[] b2 = StringConverter.hexStringToArray("56550282104C3B9ACA00");
        assertThrows(IllegalArgumentException.class, () -> ps.decodeRaw(b2, 0));
    }

    @Test
    public void testInvalidMillisecondOfDay() {
        // 86401000 = 0x05265FE8 is past the end of a leap second
        CdsTimeDecoder ctd = new CdsTimeDecoder(0x41);
        byte[] b = StringConverter.hexStringToArray("542D05265FE80000");
        assertThrows(IllegalArgumentException.class, () -> ctd.decodeDayTime(b, 0));
        assertThrows(IllegalArgumentException.class, () -> ctd.decode(b, 0));
        assertThrows(IllegalArgumentException.class, () -> ctd.decodeRaw(b, 0));
    }

    @Test
    public void testConsumesAllBytes() {
        // pfield, 3 bytes day, 4 bytes ms of day, 4 bytes picoseconds, then a sentinel byte that must remain unread
        byte[] buf = StringConverter.hexStringToArray("46000001000000010000000AAA");
        int[] pos = { 0 };
        ByteSupplier supplier = () -> buf[pos[0]++];
        new CdsTimeDecoder(-1).decode(supplier);
        assertEquals(12, pos[0]);
    }

    @Test
    public void testInvalidPfield() {
        // 0x2E is a CUC pfield
        assertThrows(IllegalArgumentException.class, () -> new CdsTimeDecoder(0x2E));
        // submillisecond length 11 is reserved
        assertThrows(IllegalArgumentException.class, () -> new CdsTimeDecoder(0x43));

        byte[] b = StringConverter.hexStringToArray("2E56550282104C");
        assertThrows(IllegalArgumentException.class, () -> new CdsTimeDecoder(-1).decode(b, 0));
    }
}
