package org.yamcs.xtce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.xml.XtceStaxReader;

public class TimeAssociationTest {
    @Test
    public void testTimeAssociationOnEntries() throws Exception {
        try (var reader = new XtceStaxReader("src/test/resources/time-association.xml")) {
            var ss = reader.readXmlDocument();
            var packet = ss.getSequenceContainer("packet");
            assertNotNull(packet);
            assertEquals(3, packet.getEntryList().size());

            var parameterEntry = (ParameterEntry) packet.getEntryList().get(1);
            var parameterTa = parameterEntry.getTimeAssociation();
            assertNotNull(parameterTa);
            assertEquals("packet_time", parameterTa.getParameter().getName());
            assertEquals(-0.8d, parameterTa.getOffset());
            assertEquals(TimeAssociation.UnitType.SI_SECOND, parameterTa.getUnit());
            assertFalse(parameterTa.isInterpolateTime());

            var containerEntry = (ContainerEntry) packet.getEntryList().get(2);
            var containerTa = containerEntry.getTimeAssociation();
            assertNotNull(containerTa);
            assertEquals("packet_time", containerTa.getParameter().getName());
            assertEquals(-600d, containerTa.getOffset());
            assertEquals(TimeAssociation.UnitType.SI_MILLSECOND, containerTa.getUnit());
            assertFalse(containerTa.isInterpolateTime());
        }
    }
}
