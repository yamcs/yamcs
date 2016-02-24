package org.yamcs.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.yamcs.parameter.Value;

public class StringConvertorsTest {
    
    @Test
    public void testUint64AndSint64Conversion() {
        Value v = ValueUtility.getUint64Value(5L);
        assertEquals("5", StringConvertors.toString(v, false));

        // Use sign-bit
        long value = 0xF0AE512C8F337E04L;
        
        // Interpret it as sint64
        v = ValueUtility.getSint64Value(value);
        assertEquals("-1103855606836265468", StringConvertors.toString(v, false));
        
        // Interpret it as uint64
        v = ValueUtility.getUint64Value(value);
        assertEquals("17342888466873286148", StringConvertors.toString(v, false));
    }
}
