package org.yamcs.xtce;

import static org.junit.Assert.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TestAncillaryData {
    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader();
        SequenceContainer seq = reader.readXmlDocument("src/test/resources/BogusSAT-1.xml")
                .getSubsystem("SC001")
                .getSubsystem("BusElectronics")
                .getSequenceContainer("SensorHistoryRecord");
        assertTrue(seq.useAsArchivePartition());
    }
}
