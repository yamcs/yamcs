package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

public class TaiUtcConverterTest {

    @BeforeAll
    static public void setUp() {
        TimeEncoding.setUp();
    }

    @Test
    public void test0() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        DateTimeComponents dtc = tuc.instantToUtc(0);
        assertEquals(1970, dtc.year);
        assertEquals(1, dtc.month);
        assertEquals(1, dtc.day);
        assertEquals(0, dtc.hour);
        assertEquals(0, dtc.minute);
        assertEquals(0, dtc.second);
    }

    @Test
    public void test2008() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        DateTimeComponents dtc = tuc.instantToUtc(1230768032000L);
        assertEquals(59, dtc.second);
    }

    @Test
    public void testNegative() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        long t = tuc.utcToInstant(new DateTimeComponents(1017, 1, 1, 11, 59, 58, 999));
        assertEquals("1017-01-01T11:59:58.999Z", TimeEncoding.toString(t));
    }

    @Test
    public void testInstantToTimestamp() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        long t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 30, 23, 59, 59, 0));
        Timestamp ts = tuc.instantToProtobuf(t);
        assertEquals("2016-12-30T23:59:59Z", Timestamps.toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(2017, 1, 1, 12, 1, 1, 3));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2017-01-01T12:01:01.003Z", Timestamps.toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 12, 0, 1, 0));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2016-12-31T12:00:00.999988427Z", Timestamps.toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 23, 59, 60, 0));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2016-12-31T23:59:59.500005787Z", Timestamps.toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 23, 59, 60, 500));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2017-01-01T00:00:00Z", Timestamps.toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(2017, 1, 1, 11, 59, 58, 999));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2017-01-01T11:59:58.999011586Z", Timestamps.toString(ts));

    }

    @Test
    public void testInstantToTimestampOutOfRange() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        long t = tuc.utcToInstant(new DateTimeComponents(20017, 1, 1, 11, 59, 58, 992));
        Timestamp ts = tuc.instantToProtobuf(t);
        Timestamps.checkValid(ts);
        assertEquals("9999-12-31T23:59:59.992Z", Timestamps.toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(-20, 1, 1, 11, 59, 58, 992));
        ts = tuc.instantToProtobuf(t);
        Timestamps.checkValid(ts);
        assertEquals("0001-01-01T00:00:00.992Z", Timestamps.toString(ts));
    }

    @Test
    public void testTimestampToInstant() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        long t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 30, 23, 59, 59, 0));
        Timestamp ts = tuc.instantToProtobuf(t);
        long t1 = tuc.protobufToInstant(ts);
        assertEquals("2016-12-30T23:59:59.000Z", TimeEncoding.toString(t1));
        assertEquals("2016-12-30T23:59:59Z", Timestamps.toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(2017, 1, 1, 12, 1, 1, 3));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("2017-01-01T12:01:01.003Z", TimeEncoding.toString(t1));
        assertEquals("2017-01-01T12:01:01.003Z", Timestamps.toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 12, 0, 1, 0));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("2016-12-31T12:00:01.000Z", TimeEncoding.toString(t1));

        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 23, 59, 60, 0));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("2016-12-31T23:59:60.000Z", TimeEncoding.toString(t1));

        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 23, 59, 60, 500));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("2016-12-31T23:59:60.500Z", TimeEncoding.toString(t1));

        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 20, 30, 17, 532));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("2016-12-31T20:30:17.532Z", TimeEncoding.toString(t1));

        t = tuc.utcToInstant(new DateTimeComponents(2017, 1, 1, 11, 59, 58, 999));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("2017-01-01T11:59:58.999Z", TimeEncoding.toString(t1));

        t = tuc.utcToInstant(new DateTimeComponents(1017, 1, 1, 11, 59, 58, 999));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("1017-01-01T11:59:58.999Z", Timestamps.toString(ts));
    }
}
