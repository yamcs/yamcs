package org.yamcs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class Mil1750ATest {

    @Test
    public void testEncode32() {
        assertEquals(0, MilStd1750A.encode32(0));
        assertEquals(0x7FFF_FF_7F, MilStd1750A.encode32(0.99999988079071044921873 * Math.pow(2, 127)));
        assertEquals(0x4000_00_7F, MilStd1750A.encode32(0.5 * Math.pow(2, 127)));

        assertEquals(0x5000_00_04, MilStd1750A.encode32(0.625 * 16));
        assertEquals(0x4000_00_01, MilStd1750A.encode32(1));
        assertEquals(0x4000_00_00, MilStd1750A.encode32(0.5));
        assertEquals(0x4000_00_FF, MilStd1750A.encode32(0.25));
        assertEquals(0x4000_00_80, MilStd1750A.encode32(0.5 * Math.pow(2, -128)));

        assertEquals(0x8000_00_00, MilStd1750A.encode32(-1));
        assertEquals(0xBFFF_FF_80, MilStd1750A.encode32(-0.5000001 * Math.pow(2, -128)));
        assertEquals(0x9FFF_FF_04, MilStd1750A.encode32(-0.7500001 * 16));

        assertEquals(0xBFFF_FF_80, MilStd1750A.encode32(-1.4693682324014469e-39));

        assertEquals(MilStd1750A.MAX_FLOAT32_VALUE, MilStd1750A.encode32(1e200));
        assertEquals(MilStd1750A.MIN_FLOAT32_VALUE, MilStd1750A.encode32(-1e200));
    }

    @Test
    public void testDecode32() {
        assertEquals(0, MilStd1750A.decode32(0), 1E-5);

        assertEquals(0.99999988079071044921873 * Math.pow(2, 127), MilStd1750A.decode32(0x7FFF_FF_7F), 1E-5);
        assertEquals(0.5 * Math.pow(2, 127), MilStd1750A.decode32(0x4000_00_7F), 1E-5);
        assertEquals(0.625 * 16, MilStd1750A.decode32(0x5000_00_04), 1E-5);
        assertEquals(1.0, MilStd1750A.decode32(0x4000_00_01), 1E-5);
        assertEquals(0.5, MilStd1750A.decode32(0x4000_00_00), 1E-5);
        assertEquals(0.25, MilStd1750A.decode32(0x4000_00_FF), 1E-5);
        assertEquals(0.5 * Math.pow(2, -128), MilStd1750A.decode32(0x4000_00_80), 1E-5);

        assertEquals(-1, MilStd1750A.decode32(0x8000_00_00), 1E-5);
        assertEquals(-0.5000001 * Math.pow(2, -128), MilStd1750A.decode32(0xBFFF_FF_80), 1E-5);
        assertEquals(-0.7500001 * 16, MilStd1750A.decode32(0x9FFF_FF_04), 1E-5);
        assertEquals(-1.4693682324014469e-39, MilStd1750A.decode32(0xBFFF_FF_80), 1E-45);
    }

    @Test
    public void testEncode48() {
        assertEquals(0, MilStd1750A.encode48(0));
        assertEquals(0x400000_7F_0000L, MilStd1750A.encode48(0.5 * Math.pow(2, 127)));
        assertEquals(0x800000_80_0000L, MilStd1750A.encode48(-Math.pow(2, -128)));
        assertEquals(0xA00000_FF_0000L, MilStd1750A.encode48(-0.75 * 0.5));
        assertEquals(0x69A3B50754ABL, MilStd1750A.encode48(105.639485637520592250));
    }

    @Test
    public void testDecode48() {
        assertEquals(0, MilStd1750A.decode48(0), 1E-10);
        assertEquals(0.5 * Math.pow(2, 127), MilStd1750A.decode48(0x400000_7F_0000L), 1E-10);
        assertEquals(-Math.pow(2, -128), MilStd1750A.decode48(0x800000_80_0000L), 1E-10);
        assertEquals(-0.75 * 0.5, MilStd1750A.decode48(0xA00000_FF_0000L), 1E-10);
        assertEquals(105.639485637520592250, MilStd1750A.decode48(0x69A3B50754ABL), 1E-10);
    }
}
