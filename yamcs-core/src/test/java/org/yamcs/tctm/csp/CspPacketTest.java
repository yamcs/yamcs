package org.yamcs.tctm.csp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

    @Test
    public void testFieldsLE() {
        var bytes = new byte[] { (byte) 0x89, (byte) 0x88, (byte) 0x0D, (byte) 0x00 };

        // Verify that a provided ByteBuffer is allowed
        // to be in Little Endian order.
        var bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        var header = new CspPacket(bb);
        assertEquals(2, header.getPriority());
        assertEquals(4, header.getSource());
        assertEquals(24, header.getDestination());
        assertEquals(13, header.getSourcePort());
        assertEquals(32, header.getDestinationPort());
        assertFalse(header.getHmacFlag());
        assertFalse(header.getXteaFlag());
        assertFalse(header.getRdpFlag());
        assertFalse(header.getCrcFlag());
    }
}
