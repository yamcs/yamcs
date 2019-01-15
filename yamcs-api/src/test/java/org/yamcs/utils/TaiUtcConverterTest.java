package org.yamcs.utils;

import static org.junit.Assert.*;

import java.time.Instant;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;

import com.google.protobuf.Timestamp;

public class TaiUtcConverterTest {
    @BeforeClass
    static public void setUp() {
        TimeEncoding.setUp();
    }

    @Test
    public void test0() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        DateTimeComponents dtc = tuc.instantToUtc(0);
        assertEquals(1969, dtc.year);
        assertEquals(12, dtc.month);
        assertEquals(31, dtc.day);
        assertEquals(23, dtc.hour);
        assertEquals(59, dtc.minute);
        assertEquals(51, dtc.second);

    }

    @Test
    public void test2008() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        DateTimeComponents dtc = tuc.instantToUtc(1230768032000L);
        assertEquals(59, dtc.second);

    }

    @Test
    public void testInstantToTimestamp() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        long t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 30, 23, 59, 59, 0));
        Timestamp ts = tuc.instantToProtobuf(t);
        assertEquals("2016-12-30T23:59:59Z", toString(ts));

        t = tuc.utcToInstant(new DateTimeComponents(2017, 1, 1, 12, 1, 1, 3));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2017-01-01T12:01:01.003Z", toString(ts));
        
        
        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 12, 0, 1, 0));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2016-12-31T12:00:00.999988427Z", toString(ts));
        
        
        
        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 23, 59, 60, 0));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2016-12-31T23:59:59.500005787Z", toString(ts));
        
        t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 31, 23, 59, 60, 500));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2017-01-01T00:00:00Z", toString(ts));
        
        
        t = tuc.utcToInstant(new DateTimeComponents(2017, 1, 1, 11, 59, 58, 999));
        ts = tuc.instantToProtobuf(t);
        assertEquals("2017-01-01T11:59:58.999011586Z", toString(ts));
        
    }
    
    
    @Test
    public void testTimestampToInstant() throws Exception {
        TaiUtcConverter tuc = new TaiUtcConverter();
        long t = tuc.utcToInstant(new DateTimeComponents(2016, 12, 30, 23, 59, 59, 0));
        Timestamp ts = tuc.instantToProtobuf(t);
        long t1 = tuc.protobufToInstant(ts);
        assertEquals("2016-12-30T23:59:59.000Z", TimeEncoding.toString(t1));

        t = tuc.utcToInstant(new DateTimeComponents(2017, 1, 1, 12, 1, 1, 3));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("2017-01-01T12:01:01.003Z", TimeEncoding.toString(t1));
        
        
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
        
        
        t = tuc.utcToInstant(new DateTimeComponents(2017, 1, 1, 11, 59, 58, 999));
        ts = tuc.instantToProtobuf(t);
        t1 = tuc.protobufToInstant(ts);
        assertEquals("2017-01-01T11:59:58.999Z", TimeEncoding.toString(t1));
        
    }
    
    
    private String toString(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()).toString();
    }
}
