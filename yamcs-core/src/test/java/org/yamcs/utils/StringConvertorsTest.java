package org.yamcs.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;

public class StringConvertorsTest {
    
    @Test
    public void testUint64AndSint64Conversion() {
        Value v = Value.newBuilder().setType(Type.UINT64).setUint64Value(5L).build();
        assertEquals("5", StringConvertors.toString(v, false));

        // Use sign-bit
        long value = 0xF0AE512C8F337E04L;
        
        // Interpret it as sint64
        v = Value.newBuilder().setType(Type.SINT64).setSint64Value(value).build();
        assertEquals("-1103855606836265468", StringConvertors.toString(v, false));
        
        // Interpret it as uint64
        v = Value.newBuilder().setType(Type.UINT64).setUint64Value(value).build();
        assertEquals("17342888466873286148", StringConvertors.toString(v, false));
    }
}
