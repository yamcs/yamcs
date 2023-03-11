package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.IntegerDataEncoding;

public class DataEncodingUtilsTest {
    @Test
    public void testGetRawIntegerValue() {
        IntegerDataEncoding ide = new IntegerDataEncoding.Builder().setSizeInBits(48).build();
        var v = DataEncodingUtils.getRawIntegerValue(ide, 0x0102030405060708L);
        assertEquals(0x030405060708L, v.getUint64Value());
    }
}
