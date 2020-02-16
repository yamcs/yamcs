package org.yamcs.xtce;

import static org.junit.Assert.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.yamcs.utils.DoubleRange;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TestInclusiveExclusiveRange {
    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader();
        SpaceSystem ss = reader.readXmlDocument("src/test/resources/BogusSAT-1.xml");
        FloatParameterType fpt = (FloatParameterType) ss.getSubsystem("SC001").getSubsystem("BusElectronics").getParameterType("Battery_Voltage_Type");
        
        DoubleRange dr =  fpt.getDefaultAlarm().getStaticAlarmRanges().getWarningRange();
        assertEquals(12.35, dr.getMin(), 1e-5);
        assertFalse(dr.isMinInclusive());
    
        assertEquals(13.80, dr.getMax(), 1e-5);
        assertFalse(dr.isMaxInclusive());
    
    }
}
