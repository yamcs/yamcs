package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.tctm.ccsds.time.CdsTimeDecoder;
import org.yamcs.tctm.ccsds.time.CdsTimeEncoder;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;
import org.yamcs.utils.TimeEncoding;

/**
 * Same test vector as in {@link CdsTimeDecoderTest}: 2018-07-06T11:41:18.284Z is day 22101 (0x5655) since 1958-01-01
 * and millisecond of day 42078284 (0x0282104C).
 */
public class CdsTimeEncoderTest {
    static long epoch1958;
    static long t;
    // the same time as raw microseconds: (22101 * 86400000 + 42078284) * 1000
    static final long T_RAW = 1_909_568_478_284_000L;

    @BeforeAll
    static public void beforeClass() {
        TimeEncoding.setUp();
        epoch1958 = TimeEncoding.parse("1958-01-01T00:00:00Z");
        t = TimeEncoding.parse("2018-07-06T11:41:18.284Z");
    }

    static String encode(CdsTimeEncoder cte, long instant) {
        byte[] b = new byte[cte.getEncodedLength()];
        int n = cte.encode(instant, b, 0);
        assertEquals(b.length, n);
        return StringConverter.arrayToHexString(b);
    }

    static String encodeRaw(CdsTimeEncoder cte, long time) {
        byte[] b = new byte[cte.getEncodedLength()];
        int n = cte.encodeRaw(time, b, 0);
        assertEquals(b.length, n);
        return StringConverter.arrayToHexString(b);
    }

    static long utc(int year, int month, int day, int hour, int minute, int second, int millisec) {
        return TimeEncoding.fromUtc(new DateTimeComponents(year, month, day, hour, minute, second, millisec));
    }

    @Test
    public void testImplicit() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x40, true, epoch1958);
        assertEquals(6, cte.getEncodedLength());
        assertEquals("56550282104C", encode(cte, t));
    }

    @Test
    public void testExplicit() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x40, false, epoch1958);
        assertEquals(7, cte.getEncodedLength());
        assertEquals("4056550282104C", encode(cte, t));
    }

    @Test
    public void test24BitDay() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x44, false, epoch1958);
        assertEquals("440056550282104C", encode(cte, t));
    }

    @Test
    public void testUnixEpoch() {
        // 1970-01-01 to 2018-07-06 is 17718 = 0x4536 days
        CdsTimeEncoder cte = new CdsTimeEncoder(0x48, false, TimeEncoding.parse("1970-01-01T00:00:00Z"));
        assertEquals("4845360282104C", encode(cte, t));
    }

    @Test
    public void testMicroseconds() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x41, true, epoch1958);
        // the microsecond segment is 0 when encoding an instant
        assertEquals("56550282104C0000", encode(cte, t));
        // raw time is in microseconds: 789 us = 0x0315
        assertEquals("56550282104C0315", encodeRaw(cte, T_RAW + 789));
    }

    @Test
    public void testPicoseconds() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x42, true, epoch1958);
        assertEquals("56550282104C00000000", encode(cte, t));
        // 789 us = 789000000 ps = 0x2F072F40
        assertEquals("56550282104C2F072F40", encodeRaw(cte, T_RAW + 789));
    }

    @Test
    public void testLeapSecond() {
        // 2016-12-31 is day 21549 = 0x542D since 1958-01-01 and ends with a leap second
        CdsTimeEncoder cte = new CdsTimeEncoder(0x40, true, epoch1958);
        // 23:59:59.999: 86399999 = 0x05265BFF
        assertEquals("542D05265BFF", encode(cte, utc(2016, 12, 31, 23, 59, 59, 999)));
        // 23:59:60.000 and 23:59:60.500: 86400000 = 0x05265C00 and 86400500 = 0x05265DF4
        assertEquals("542D05265C00", encode(cte, utc(2016, 12, 31, 23, 59, 60, 0)));
        assertEquals("542D05265DF4", encode(cte, utc(2016, 12, 31, 23, 59, 60, 500)));
        // 23:59:60.999: 86400999 = 0x05265FE7
        assertEquals("542D05265FE7", encode(cte, utc(2016, 12, 31, 23, 59, 60, 999)));
        // the next millisecond is 2017-01-01T00:00:00.000, day 21550 = 0x542E
        assertEquals("542E00000000", encode(cte, utc(2016, 12, 31, 23, 59, 60, 999) + 1));
    }

    @Test
    public void testDecoderReadsLeapSecond() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x41, false, epoch1958);
        CdsTimeDecoder ctd = new CdsTimeDecoder(-1);
        byte[] b = new byte[cte.getEncodedLength()];
        cte.encode(utc(2016, 12, 31, 23, 59, 60, 500), b, 0);
        assertEquals(new CdsTimeDecoder.DayTime(21549, 86_400_500), ctd.decodeDayTime(b, 0));
    }

    @Test
    public void testRoundTripRaw() {
        for (int pfield : new int[] { 0x40, 0x41, 0x42, 0x44, 0x45, 0x46, 0x48 }) {
            CdsTimeEncoder cte = new CdsTimeEncoder(pfield, false, epoch1958);
            CdsTimeDecoder ctd = new CdsTimeDecoder(-1);
            byte[] b = new byte[cte.getEncodedLength()];
            long raw = T_RAW + 123;
            cte.encodeRaw(raw, b, 0);
            assertEquals(pfield == 0x40 || pfield == 0x44 || pfield == 0x48 ? T_RAW : raw, ctd.decodeRaw(b, 0));
        }
    }

    @Test
    public void testOutOfRange() {
        CdsTimeEncoder cte = new CdsTimeEncoder(0x40, true, epoch1958);
        byte[] b = new byte[6];
        // the 16 bit day segment holds days 0 to 65535, so day 65536 does not fit
        long day65536 = TimeEncoding.parse(LocalDate.of(1958, 1, 1).plusDays(65536) + "T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> cte.encode(day65536, b, 0));
        assertEquals(6, cte.encode(day65536 - 1, b, 0));
        // last millisecond of day 65535: 86399999 = 0x05265BFF
        assertEquals("FFFF05265BFF", StringConverter.arrayToHexString(b));

        // day 0, and the millisecond before it
        assertEquals("000000000000", encode(cte, epoch1958));
        assertThrows(IllegalArgumentException.class, () -> cte.encode(epoch1958 - 1, b, 0));
        assertThrows(IllegalArgumentException.class, () -> cte.encodeRaw(-1, b, 0));
    }

    @Test
    public void testInvalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> new CdsTimeEncoder(0x2E, true, epoch1958));
        // the epoch has to be the start of a day
        long noon = TimeEncoding.parse("1958-01-01T12:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new CdsTimeEncoder(0x40, true, noon));
    }
}
