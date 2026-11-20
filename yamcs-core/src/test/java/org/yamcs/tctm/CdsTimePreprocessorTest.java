package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.ccsds.time.CdsTimeEncoder;
import org.yamcs.tctm.cfs.CfsPacketPreprocessor;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yaml.snakeyaml.Yaml;

/**
 * Checks the CDS time decoder configured in a packet preprocessor, including the conversion from UTC days to Yamcs
 * time and the leap seconds (CCSDS 301.0-B-4 Annex A).
 */
public class CdsTimePreprocessorTest {

    @BeforeAll
    static public void beforeClass() {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(true);
    }

    static GenericPacketPreprocessor preprocessor(Map<String, Object> timeEncoding) {
        return preprocessor(timeEncoding, Map.of());
    }

    static GenericPacketPreprocessor preprocessor(Map<String, Object> timeEncoding, Map<String, Object> otherArgs) {
        Map<String, Object> args = new HashMap<>(otherArgs);
        args.put("timestampOffset", 0);
        args.put("seqCountOffset", -1);
        args.put("timeEncoding", timeEncoding);
        return new GenericPacketPreprocessor("test", YConfiguration.wrap(args));
    }

    @Test
    public void test1958Epoch() {
        var pp = preprocessor1958();

        // day 22101, ms of day 42078284 (see CdsTimeDecoderTest)
        byte[] packet = StringConverter.hexStringToArray("56550282104C00000000");
        TmPacket tmPacket = pp.process(new TmPacket(TimeEncoding.getWallclockTime(), packet));

        assertFalse(tmPacket.isInvalid());
        assertEquals(TimeEncoding.parse("2018-07-06T11:41:18.284Z"), tmPacket.getGenerationTime());
        assertEquals("2018-07-06T11:41:18.284Z", TimeEncoding.toString(tmPacket.getGenerationTime()));
    }

    @Test
    public void testUnixEpoch() {
        // agency defined epoch 1970-01-01 (pfield 0x48), explicit pfield in the packet
        var pp = preprocessor(Map.of("type", "CDS", "epochDate", "1970-01-01"));

        // 1970-01-01 to 2018-07-06 is 17718 = 0x4536 days
        byte[] packet = StringConverter.hexStringToArray("4845360282104C00000000");
        TmPacket tmPacket = pp.process(new TmPacket(TimeEncoding.getWallclockTime(), packet));

        assertFalse(tmPacket.isInvalid());
        assertEquals("2018-07-06T11:41:18.284Z", TimeEncoding.toString(tmPacket.getGenerationTime()));
    }

    static GenericPacketPreprocessor preprocessor1958() {
        return preprocessor(Map.of("type", "CDS", "implicitPField", 0x40, "epochDate", "1958-01-01"));
    }

    @Test
    public void testEpochDateFromYaml() {
        // YAML reads an unquoted date as a java.util.Date
        Map<String, Object> timeEncoding = new Yaml().load("type: CDS\nimplicitPField: 0x40\nepochDate: 1958-01-01");
        var pp = preprocessor(timeEncoding);
        assertEquals("2018-07-06T11:41:18.284Z", genTime(pp, "56550282104C"));
    }

    static TmPacket process(GenericPacketPreprocessor pp, String hex) {
        return pp.process(new TmPacket(TimeEncoding.getWallclockTime(), StringConverter.hexStringToArray(hex)));
    }

    static String genTime(GenericPacketPreprocessor pp, String hex) {
        return TimeEncoding.toString(process(pp, hex).getGenerationTime());
    }

    static long utc(int year, int month, int day, int hour, int minute, int second, int millisec) {
        return TimeEncoding.fromUtc(new DateTimeComponents(year, month, day, hour, minute, second, millisec));
    }

    @Test
    public void testLeapSecond() {
        var pp = preprocessor1958();
        // 2016-12-31 (day 21549 = 0x542D) ends with a leap second
        // 86399999 = 0x05265BFF, 86400000 = 0x05265C00, 86400500 = 0x05265DF4, 86400999 = 0x05265FE7
        assertEquals("2016-12-31T23:59:59.999Z", genTime(pp, "542D05265BFF"));
        assertEquals("2016-12-31T23:59:60.000Z", genTime(pp, "542D05265C00"));
        TmPacket leap = process(pp, "542D05265DF4");
        assertEquals("2016-12-31T23:59:60.500Z", TimeEncoding.toString(leap.getGenerationTime()));
        assertEquals("2016-12-31T23:59:60.999Z", genTime(pp, "542D05265FE7"));

        // 2017-01-01T00:00:00.500Z (day 21550 = 0x542E, 500 = 0x01F4) is one second after the leap second
        TmPacket next = process(pp, "542E000001F4");
        assertEquals("2017-01-01T00:00:00.500Z", TimeEncoding.toString(next.getGenerationTime()));
        assertEquals(1000, next.getGenerationTime() - leap.getGenerationTime());
    }

    @Test
    public void testLeapSecond1970Epoch() {
        // 1970-01-01 to 2016-12-31 is 17166 = 0x430E days
        var pp = preprocessor(Map.of("type", "CDS", "implicitPField", 0x48, "epochDate", "1970-01-01"));
        assertEquals("2016-12-31T23:59:60.500Z", genTime(pp, "430E05265DF4"));
    }

    static void assertTimeError(GenericPacketPreprocessor pp, String hex, String expectedMessage) {
        EventProducerFactory.getMockupQueue().clear();
        TmPacket tmPacket = process(pp, hex);
        assertEquals(TimeEncoding.INVALID_INSTANT, tmPacket.getGenerationTime());
        assertEquals(1, EventProducerFactory.getMockupQueue().size());
        Event ev = EventProducerFactory.getMockupQueue().poll();
        assertTrue(ev.getMessage().contains(expectedMessage), ev.getMessage());
    }

    @Test
    public void testNoLeapSecondThatDay() {
        var pp = preprocessor1958();
        // 2016-12-30 (day 21548 = 0x542C) has no leap second, so 86400000 is not a valid millisecond of day
        assertTimeError(pp, "542C05265C00", "day 21548: that day has no leap second");
        // 86401000 = 0x05265FE8 is never valid
        assertTimeError(pp, "542D05265FE8", "Invalid CDS millisecond of day 86401000");
    }

    static void assertConfigError(Map<String, Object> timeEncoding, String expectedMessage) {
        var e = assertThrows(ConfigurationException.class, () -> preprocessor(timeEncoding));
        assertTrue(e.getMessage().contains(expectedMessage), e.getMessage());
    }

    @Test
    public void testConfigErrors() {
        // the generic epoch options do not apply to CDS
        assertConfigError(Map.of("type", "CDS", "epochDate", "1958-01-01", "epoch", "UNIX"), "'epoch'");
        assertConfigError(Map.of("type", "CDS", "epoch", "CUSTOM", "epochUTC", "1958-01-01T00:00:00Z",
                "timeIncludesLeapSeconds", false), "cannot be used with the CDS time decoder");
        assertConfigError(Map.of("type", "CDS", "epochDate", "1958-01-01", "timeIncludesLeapSeconds", false),
                "'timeIncludesLeapSeconds'");
        // no silent default
        assertConfigError(Map.of("type", "CDS"), "requires the option 'epochDate'");
        // the raw time needs a time correlation service
        assertConfigError(Map.of("type", "CDS", "epochDate", "NONE"), "requires a time correlation service");
        // not a date
        assertConfigError(Map.of("type", "CDS", "epochDate", "1958-13-01"), "invalid epochDate");
        assertConfigError(Map.of("type", "CDS", "epochDate", "1958-01-01T00:00:00Z"), "invalid epochDate");
        assertConfigError(Map.of("type", "CDS", "epochDate", 1958), "invalid epochDate");
        // an unquoted date and time in the YAML file
        Map<String, Object> noon = new Yaml().load("type: CDS\nepochDate: 1958-01-01T12:00:00Z");
        assertConfigError(noon, "epochDate has to be a date without a time");

        // the CFS preprocessor ignores the decoder and would use the generic epoch
        var e = assertThrows(ConfigurationException.class, () -> new CfsPacketPreprocessor("test", YConfiguration
                .wrap(Map.of("timeEncoding", Map.of("type", "CDS", "epochDate", "1958-01-01")))));
        assertTrue(e.getMessage().contains("cannot be CDS"), e.getMessage());
    }

    @Test
    public void testRoundTripAroundLeapSeconds() {
        var pp = preprocessor1958();
        CdsTimeEncoder cte = new CdsTimeEncoder(0x40, true, LocalDate.of(1958, 1, 1));
        // two leap seconds (end of June and end of December) and a day without one
        long[] starts = { utc(2015, 6, 30, 23, 59, 58, 0), utc(2016, 12, 31, 23, 59, 58, 0),
                utc(2016, 12, 30, 23, 59, 58, 0) };
        for (long start : starts) {
            for (long t = start; t < start + 4000; t++) {
                byte[] b = new byte[cte.getEncodedLength()];
                cte.encode(t, b, 0);
                TmPacket tmPacket = pp.process(new TmPacket(TimeEncoding.getWallclockTime(), b));
                assertEquals(t, tmPacket.getGenerationTime(), TimeEncoding.toString(t));
            }
        }
    }
}
