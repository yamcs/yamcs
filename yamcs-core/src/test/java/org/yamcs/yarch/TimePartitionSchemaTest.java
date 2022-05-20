package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeEncoding;

public class TimePartitionSchemaTest {

    @BeforeAll
    public static void setup() {
        TimeEncoding.setUp();
    }

    @Test
    public void testYYYWithFuture() {
        long instant = TimeEncoding.parse("10000-02-03T04:05:06Z");

        TimePartitionSchema yyyy = TimePartitionSchema.getInstance("YYYY");
        TimePartitionInfo tpi = yyyy.getPartitionInfo(instant);
        assertEquals("10000", tpi.getDir());
        assertEquals(TimeEncoding.parse("10000-01-01T00:00:00Z"), tpi.getStart());

        tpi = yyyy.parseDir("99993");
        assertEquals(TimeEncoding.parse("99993-01-01T00:00:00Z"), tpi.getStart());

    }

    @Test
    public void testYYYWithPast() {
        long instant = TimeEncoding.parse("0001-02-03T04:05:06Z");

        TimePartitionSchema yyyy = TimePartitionSchema.getInstance("YYYY");
        TimePartitionInfo tpi = yyyy.getPartitionInfo(instant);
        assertEquals("0001", tpi.getDir());
        assertEquals(TimeEncoding.parse("0001-01-01T00:00:00Z"), tpi.getStart());
        assertEquals(TimeEncoding.parse("0002-01-01T00:00:00Z"), tpi.getEnd());
    }

    @Test
    public void testYYYMMDDWithPast() {
        long instant = TimeEncoding.parse("0001-02-03T04:05:06Z");

        TimePartitionSchema yyyy = TimePartitionSchema.getInstance("YYYY/MM");
        TimePartitionInfo tpi = yyyy.getPartitionInfo(instant);
        assertEquals("0001/02", tpi.getDir());
        assertEquals(TimeEncoding.parse("0001-02-01T00:00:00Z"), tpi.getStart());
        assertEquals(TimeEncoding.parse("0001-03-01T00:00:00Z"), tpi.getEnd());
    }

    @Test
    public void testYYYDOYWithPast() {
        long instant = TimeEncoding.parse("0001-02-03T04:05:06Z");

        TimePartitionSchema yyyydoy = TimePartitionSchema.getInstance("YYYY/DOY");
        TimePartitionInfo tpi = yyyydoy.getPartitionInfo(instant);
        assertEquals("0001/034", tpi.getDir());

        assertEquals(TimeEncoding.parse("0001-02-03T00:00:00Z"), tpi.getStart());

        long instant1 = TimeEncoding.parse("0001-12-31T04:05:06Z");
        TimePartitionInfo tpi1 = yyyydoy.getPartitionInfo(instant1);
        assertEquals("0001/365", tpi1.getDir());
        assertEquals(TimeEncoding.parse("0001-12-31T00:00:00Z"), tpi1.getStart());
        assertEquals(TimeEncoding.parse("0002-01-01T00:00:00Z"), tpi1.getEnd());
    }

    @Test
    public void testYYYMM() {
        long instant = TimeEncoding.parse("2003-11-03T04:05:06Z");

        TimePartitionSchema yyyymm = TimePartitionSchema.getInstance("YYYY/MM");
        TimePartitionInfo tpi = yyyymm.getPartitionInfo(instant);
        assertEquals("2003/11", tpi.getDir());
        assertEquals("2003-11-01T00:00:00.000Z", TimeEncoding.toString(tpi.getStart()));
        assertEquals("2003-12-01T00:00:00.000Z", TimeEncoding.toString(tpi.getEnd()));

        TimePartitionInfo tpi1 = yyyymm.getPartitionInfo(TimeEncoding.parse("2003-12-31T23:59:59.999Z"));
        assertEquals("2003/12", tpi1.getDir());
        assertEquals("2003-12-01T00:00:00.000Z", TimeEncoding.toString(tpi1.getStart()));
        assertEquals("2004-01-01T00:00:00.000Z", TimeEncoding.toString(tpi1.getEnd()));
    }
}
