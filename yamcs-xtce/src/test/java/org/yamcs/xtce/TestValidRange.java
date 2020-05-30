package org.yamcs.xtce;


import static org.junit.Assert.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TestValidRange {
    @Test
    public void testXtce11Range() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
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
    
    @Test
    public void testFloatArgRangeXTCE12() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader();
        SpaceSystem ss = reader.readXmlDocument("src/test/resources/ranges-test.xml");
        FloatArgumentType argType = (FloatArgumentType) ss.getMetaCommand("SetTemperature")
                .getArgument("temperature")
                .getArgumentType();
        FloatValidRange range = argType.getValidRange();
        assertEquals(10.0, range.getMin(), 1e-6);
        assertEquals(40.0, range.getMax(), 1e-6);
        assertTrue(range.isMinInclusive());
        assertFalse(range.isMaxInclusive());
        assertTrue(range.isValidRangeAppliesToCalibrated());
    }
   
    @Test
    public void testParamRange() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader();
        SpaceSystem ss = reader.readXmlDocument("src/test/resources/ranges-test.xml");
        FloatParameterType ptype = (FloatParameterType) ss.getParameter("latitude").getParameterType();
        FloatValidRange range = ptype.getValidRange();
        
        assertEquals(-90.0, range.getMin(), 1e-6);
        assertEquals(90.0, range.getMax(), 1e-6);
        assertTrue(range.isMinInclusive());
        assertTrue(range.isMaxInclusive());
        assertTrue(range.isValidRangeAppliesToCalibrated());
    }
}
