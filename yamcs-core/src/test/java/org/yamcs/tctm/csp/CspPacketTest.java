package org.yamcs.tctm.csp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

public class CspPacketTest {

    @Test
    public void testFields() {
        var bytes = new byte[] { (byte) 0x90, (byte) 0x40, (byte) 0x60, (byte) 0x00, (byte) 0x32 };
        var packet = new CspPacket(bytes);
        assertEquals(2, packet.getPriority());
        assertEquals(8, packet.getSource());
        assertEquals(4, packet.getDestination());
        assertEquals(32, packet.getSourcePort());
        assertEquals(1, packet.getDestinationPort());
        assertFalse(packet.getHmacFlag());
        assertFalse(packet.getXteaFlag());
        assertFalse(packet.getRdpFlag());
        assertFalse(packet.getCrcFlag());
    }
}
