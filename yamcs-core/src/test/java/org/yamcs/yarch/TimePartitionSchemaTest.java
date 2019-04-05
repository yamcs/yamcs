package org.yamcs.yarch;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.utils.TimeEncoding;

public class TimePartitionSchemaTest {
    @BeforeClass
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
}
