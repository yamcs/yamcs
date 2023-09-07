package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TestAncillaryData {
    @Test
    public void test1() throws IllegalArgumentException, IllegalAccessException, XMLStreamException, IOException {
        XtceStaxReader reader = new XtceStaxReader("src/test/resources/BogusSAT-1.xml");
        SequenceContainer seq = reader.readXmlDocument()
                .getSubsystem("SC001")
                .getSubsystem("BusElectronics")
                .getSequenceContainer("SensorHistoryRecord");
        assertTrue(seq.useAsArchivePartition());
    }
}
