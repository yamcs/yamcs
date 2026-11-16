package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;

public class DataEncodingUtilsTest {
    @Test
    public void testGetRawIntegerValue() {
        IntegerDataEncoding ide = new IntegerDataEncoding.Builder().setSizeInBits(48).build();
        var v = DataEncodingUtils.getRawIntegerValue(ide, 0x0102030405060708L);
        assertEquals(0x030405060708L, v.getUint64Value());
    }

    @Test
    public void testGetRawIntegerValueSignedKeepsSign() {
        for (Encoding enc : new Encoding[] { Encoding.SIGN_MAGNITUDE, Encoding.ONES_COMPLEMENT,
                Encoding.TWOS_COMPLEMENT }) {
            IntegerDataEncoding ide = new IntegerDataEncoding.Builder().setSizeInBits(8).setEncoding(enc).build();
            assertEquals(-3, DataEncodingUtils.getRawIntegerValue(ide, -3L).getSint32Value());
            ide = new IntegerDataEncoding.Builder().setSizeInBits(48).setEncoding(enc).build();
            assertEquals(-3L, DataEncodingUtils.getRawIntegerValue(ide, -3L).getSint64Value());
        }
    }

    @Test
    public void testToEncodedBits() {
        assertEquals(0x83, DataEncodingEncoder.toEncodedBits(Encoding.SIGN_MAGNITUDE, -3, 8));
        assertEquals(0x03, DataEncodingEncoder.toEncodedBits(Encoding.SIGN_MAGNITUDE, 3, 8));
        assertEquals(0x800000000003L, DataEncodingEncoder.toEncodedBits(Encoding.SIGN_MAGNITUDE, -3, 48));
        assertEquals(0xFC, DataEncodingEncoder.toEncodedBits(Encoding.ONES_COMPLEMENT, -3, 8));
        assertEquals(0x03, DataEncodingEncoder.toEncodedBits(Encoding.ONES_COMPLEMENT, 3, 8));
        assertEquals(0xFFFFFFFFFFFCL, DataEncodingEncoder.toEncodedBits(Encoding.ONES_COMPLEMENT, -3, 48));
        assertEquals(0xFD, DataEncodingEncoder.toEncodedBits(Encoding.TWOS_COMPLEMENT, -3, 8));
    }
}
