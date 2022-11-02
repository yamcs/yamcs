package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.protobuf.Timestamp;

public class TimestampUtilsTest {

    @Test
    public void testCurrent() {
        Timestamp ts = TimestampUtil.currentTimestamp();
        assertTrue(ts.getSeconds() * 1000 - System.currentTimeMillis() < 1000);
    }

    @Test
    public void testFromJava() {
        Timestamp ts = TimestampUtil.java2Timestamp(1002);
        assertEquals(1L, ts.getSeconds());
        assertEquals(2_000_000L, ts.getNanos());
    }

    @Test
    public void testToJava() {
        Timestamp ts = TimestampUtil.java2Timestamp(1002);
        assertEquals(1002, TimestampUtil.timestamp2Java(ts));
    }
}
