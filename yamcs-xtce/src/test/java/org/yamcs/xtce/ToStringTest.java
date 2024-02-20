package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class ToStringTest {
    @Test
    public void testFloatToString() throws Exception {
        try (var reader = new XtceStaxReader("src/test/resources/to-string.xml")) {
            var ss = reader.readXmlDocument();
            var argType = (FloatParameterType) ss.getParameterType("example1_t");
            var numberFormat = argType.getNumberFormat();
            assertNotNull(numberFormat);
            assertEquals(0, numberFormat.getMinimumFractionDigits());
            assertEquals(4, numberFormat.getMaximumFractionDigits());
            assertEquals(4, numberFormat.getMinimumIntegerDigits());
            assertEquals(-1, numberFormat.getMaximumIntegerDigits());
            assertNull(numberFormat.getPositiveSuffix());
            assertNull(numberFormat.getNegativeSuffix());
            assertEquals("+", numberFormat.getPositivePrefix());
            assertEquals("-", numberFormat.getNegativePrefix());
            assertFalse(numberFormat.isShowThousandsGrouping());
            assertEquals(FloatingPointNotationType.NORMAL, numberFormat.getNotation());

            argType = (FloatParameterType) ss.getParameterType("example2_t");
            numberFormat = argType.getNumberFormat();
            assertNotNull(numberFormat);
            assertEquals(FloatingPointNotationType.SCIENTIFIC, numberFormat.getNotation());
        }
    }

    @Test
    public void testIntegerToString() throws Exception {
        try (var reader = new XtceStaxReader("src/test/resources/to-string.xml")) {
            var ss = reader.readXmlDocument();
            var argType = (IntegerParameterType) ss.getParameterType("example3_t");
            var numberFormat = argType.getNumberFormat();
            assertNotNull(numberFormat);
            assertEquals(RadixType.HEXADECIMAL, numberFormat.getNumberBase());
        }
    }
}
