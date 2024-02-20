package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TestInclusiveExclusiveRange {
    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        try (XtceStaxReader reader = new XtceStaxReader("src/test/resources/BogusSAT-1.xml")) {
            SpaceSystem ss = reader.readXmlDocument();
            FloatParameterType fpt = (FloatParameterType) ss.getSubsystem("SC001").getSubsystem("BusElectronics")
                    .getParameterType("Battery_Voltage_Type");

            DoubleRange dr = fpt.getDefaultAlarm().getStaticAlarmRanges().getWarningRange();
            assertEquals(12.35, dr.getMin(), 1e-5);
            assertFalse(dr.isMinInclusive());

            assertEquals(13.80, dr.getMax(), 1e-5);
            assertFalse(dr.isMaxInclusive());
        }
    }
}
