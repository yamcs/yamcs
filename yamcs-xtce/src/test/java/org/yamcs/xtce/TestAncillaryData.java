package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // BogusSAT-1.xml uses the old UseAsArchivingPartition property
        assertTrue(seq.useAsPacketName());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testPacketNameProperty() {
        SequenceContainer seq = new SequenceContainer("test");
        assertFalse(seq.useAsPacketName());
        seq.addAncillaryData(new AncillaryData(AncillaryData.KEY_YAMCS, AncillaryData.PROP_USE_AS_PACKET_NAME));
        assertTrue(seq.useAsPacketName());

        seq.useAsPacketName(false);
        assertFalse(seq.useAsPacketName());
        assertTrue(seq.getAncillaryData().isEmpty());

        // the old name is written for compatibility with older Yamcs versions
        seq.useAsPacketName(true);
        assertEquals(1, seq.getAncillaryData().size());
        assertEquals(AncillaryData.PROP_USE_AS_ARCHIVING_PARTITION, seq.getAncillaryData().get(0).getValue());
        assertTrue(seq.useAsPacketName());
    }
}
