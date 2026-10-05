package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class IntegerDataTypeTest {

    @Test
    public void testParseString() {
        testParseString(getidt(3, false), "", 7, true); // empty string -> exception
        testParseString(getidt(3, false), "abc", 7, true); // invalid characters -> exception
        testParseString(getidt(3, false), "0b+111", 7, true);// sign in the middle -> exception
        testParseString(getidt(3, false), "0b-111", 7, true);// sign in the middle -> exception

        testParseString(getidt(3, false), "0b111", 7, false);
        testParseString(getidt(3, false), "+0B111", 7, false);
        testParseString(getidt(4, true), "-0b111", -7, false);
        testParseString(getidt(3, true), "0b111", 0, true);// signed number too big to fit -> exception
        testParseString(getidt(3, false), "-0b111", 7, true);// negative for unsigned -> exception

        testParseString(getidt(64, false), "0b" + Long.toBinaryString(0xFFFFFFFA_FFFFFFFFl), 0xFFFFFFFA_FFFFFFFFl,
                false);

        testParseString(getidt(32, true), "10", 10, false);
        testParseString(getidt(32, true), "-10", -10, false);
        testParseString(getidt(32, false), "-10", 0, true);// negative for unsigned -> exception

        testParseString(getidt(32, false), "0xFFFFFFFF", 0xffffffffL, false);

        testParseString(getidt(64, false), "0XFFFFFFFF_FFFFFFFF", 0xffffffff_ffffffffL, false);
        testParseString(getidt(64, true), "0xFFFFFFFF_FFFFFFFF", 0xffffffff_ffffffffL, true);// signed number too big to
                                                                                             // fit -> exception

        testParseString(getidt(10, true), "0o8", 0, true); // 8 is not an octal digit -> exception
        testParseString(getidt(10, true), "0o7", 7, false);
        testParseString(getidt(10, true), "0O77", 7 * 8 + 7, false);

        testParseString(getidt(32, false), "3735928559", 3735928559L, false);

        // signed min/max boundaries (two's complement asymmetry)
        testParseString(getidt(8, true), "-128", -128, false);
        testParseString(getidt(8, true), "127", 127, false);
        testParseString(getidt(8, true), "-129", 0, true); // too negative -> exception
        testParseString(getidt(8, true), "128", 0, true); // too positive -> exception

        testParseString(getidt(16, true), "-32768", -32768, false);
        testParseString(getidt(16, true), "32767", 32767, false);
        testParseString(getidt(16, true), "-32769", 0, true);
        testParseString(getidt(16, true), "32768", 0, true);

        testParseString(getidt(32, true), "-2147483648", -2147483648L, false);
        testParseString(getidt(32, true), "2147483647", 2147483647L, false);
        testParseString(getidt(32, true), "-2147483649", 0, true);
        testParseString(getidt(32, true), "2147483648", 0, true);
    }

    private void testParseString(IntegerDataType idt, String stringValue, long expected, boolean exceptionExpected) {
        NumberFormatException nfe = null;
        long actual = -1;
        try {
            actual = idt.convertType(stringValue);
        } catch (NumberFormatException e) {
            nfe = e;
        }
        if (exceptionExpected) {
            assertNotNull(nfe);
        } else {
            assertNull(nfe);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testConvertNumberSignedBoundaries() {
        assertEquals(-128L, getidt(8, true).convertType(Long.valueOf(-128)));
        assertEquals(127L, getidt(8, true).convertType(Long.valueOf(127)));
        assertThrows(NumberFormatException.class, () -> getidt(8, true).convertType(Long.valueOf(-129)));
        assertThrows(NumberFormatException.class, () -> getidt(8, true).convertType(Long.valueOf(128)));

        assertEquals(-32768L, getidt(16, true).convertType(Long.valueOf(-32768)));
        assertEquals(32767L, getidt(16, true).convertType(Long.valueOf(32767)));
        assertThrows(NumberFormatException.class, () -> getidt(16, true).convertType(Long.valueOf(-32769)));
        assertThrows(NumberFormatException.class, () -> getidt(16, true).convertType(Long.valueOf(32768)));

        assertEquals(-2147483648L, getidt(32, true).convertType(Long.valueOf(-2147483648L)));
        assertEquals(2147483647L, getidt(32, true).convertType(Long.valueOf(2147483647L)));
        assertThrows(NumberFormatException.class, () -> getidt(32, true).convertType(Long.valueOf(-2147483649L)));
        assertThrows(NumberFormatException.class, () -> getidt(32, true).convertType(Long.valueOf(2147483648L)));

        // Long.MIN_VALUE is the signed 64 bit minimum; -Long.MIN_VALUE overflows back to itself, which
        // a naive magnitude-based check would mishandle.
        assertEquals(Long.MIN_VALUE, getidt(64, true).convertType(Long.valueOf(Long.MIN_VALUE)));
        assertEquals(Long.MAX_VALUE, getidt(64, true).convertType(Long.valueOf(Long.MAX_VALUE)));
        assertEquals(-1L, getidt(64, false).convertType(Long.valueOf(-1L))); // 2^64-1 as unsigned bit pattern
    }

    @Test
    public void testConvertNumberDoubleFloat() {
        // Truncation of in-range Double/Float is a valid "cast" and must keep working.
        assertEquals(3L, getidt(8, true).convertType(Double.valueOf(3.7)));
        assertEquals(-3L, getidt(8, true).convertType(Double.valueOf(-3.7)));
        assertEquals(3L, getidt(8, true).convertType(Float.valueOf(3.7f)));

        // NaN and Infinity must be rejected, not silently coerced to 0 / MAX / MIN.
        assertThrows(NumberFormatException.class, () -> getidt(64, true).convertType(Double.valueOf(Double.NaN)));
        assertThrows(NumberFormatException.class,
                () -> getidt(64, true).convertType(Double.valueOf(Double.POSITIVE_INFINITY)));
        assertThrows(NumberFormatException.class,
                () -> getidt(64, true).convertType(Double.valueOf(Double.NEGATIVE_INFINITY)));

        // Magnitude far too large for a long -> must be rejected, not saturated to Long.MAX_VALUE.
        assertThrows(NumberFormatException.class, () -> getidt(64, true).convertType(Double.valueOf(1e300)));
        assertThrows(NumberFormatException.class, () -> getidt(64, true).convertType(Double.valueOf(-1e300)));

        // Precise boundary around 2^63: Long.MIN_VALUE is exactly representable and must be accepted;
        // 2^63 itself (one past Long.MAX_VALUE, since Long.MAX_VALUE is not exactly representable as a
        // double) must be rejected on both sides.
        assertEquals(Long.MIN_VALUE, getidt(64, true).convertType(Double.valueOf(-0x1p63))); // -9223372036854775808.0
        assertThrows(NumberFormatException.class, () -> getidt(64, true).convertType(Double.valueOf(0x1p63))); // 2^63
        assertThrows(NumberFormatException.class,
                () -> getidt(64, true).convertType(Double.valueOf(-0x1.0000000000001p63))); // just past -2^63
    }

    @Test
    public void testParse() {
        IntegerDataType idt = getidt(64, false);
        assertNull(idt.getInitialValue());

        idt = getidt(64, true);
        assertNull(idt.getInitialValue());

        Long x = idt.convertType("-0x7FFFFFFF_00000000");
        assertEquals("-9223372032559808512", x.toString());
    }

    private IntegerDataType getidt(int sizeInBits, boolean signed) {
        IntegerParameterType.Builder idt = new IntegerParameterType.Builder().setName("test");
        idt.setSigned(signed);
        idt.setSizeInBits(sizeInBits);
        return idt.build();
    }
}
