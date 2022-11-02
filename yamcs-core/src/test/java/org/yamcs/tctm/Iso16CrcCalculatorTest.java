package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.StringConverter;

public class Iso16CrcCalculatorTest {
    Iso16CrcCalculator crcCalculator = new Iso16CrcCalculator();

    @Test
    public void test1() {
        byte[] b1 = StringConverter.hexStringToArray("0000");
        assertEquals(0xFFFF, crcCalculator.compute(b1, 0, b1.length));

        byte[] b2 = StringConverter.hexStringToArray("000000");
        assertEquals(0xFFFF, crcCalculator.compute(b2, 0, b2.length));
    }

    @Test
    public void test2() {
        byte[] b1 = StringConverter.hexStringToArray("FFFF");
        assertEquals(0xFFFF, crcCalculator.compute(b1, 0, b1.length));

        byte[] b2 = StringConverter.hexStringToArray("FFFFFF");
        assertEquals(0xFFFF, crcCalculator.compute(b2, 0, b2.length));
    }

    @Test
    public void test3() {
        byte[] b3 = StringConverter.hexStringToArray("ABCDEF01");
        assertEquals(0x9CF8, crcCalculator.compute(b3, 0, b3.length));

        byte[] b4 = StringConverter.hexStringToArray("1456F89A0001");
        assertEquals(0x24DC, crcCalculator.compute(b4, 0, b4.length));
    }
}
