package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
