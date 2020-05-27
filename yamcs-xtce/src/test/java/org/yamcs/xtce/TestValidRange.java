package org.yamcs.xtce;


import static org.junit.Assert.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TestValidRange {
    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader();
        SpaceSystem ss = reader.readXmlDocument("src/test/resources/BogusSAT-1.xml");
        IntegerArgumentType argType = ( IntegerArgumentType) ss.getSubsystem("SC001")
                .getSubsystem("BusElectronics")
                .getMetaCommand("Reaction_Wheel_Control")
                .getArgument("RW_UNIT_ID")
                .getArgumentType();
        IntegerValidRange range = argType.getValidRange();
        assertEquals(1, range.getMinInclusive());
        assertEquals(2, range.getMaxInclusive());
        assertTrue(range.isValidRangeAppliesToCalibrated());
    }
}
