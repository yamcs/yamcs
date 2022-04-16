package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.PduDecodingException;
import org.yamcs.cfdp.pdu.TLV;
import org.yamcs.utils.StringConverter;

public class PacketParseTest {

    @Test
    public void testEofPDU1() {
        byte[] b = StringConverter.hexStringToArray("00000A130018000000010015048000000000000065fc");
        EofPacket p = (EofPacket) CfdpPacket.getCFDPPacket(ByteBuffer.wrap(b));
        assertEquals(ConditionCode.INACTIVITY_DETECTED, p.getConditionCode());
    }

    @Test
    public void testEofPDU2() {
        TLV tlv = new TLV((byte) 3, new byte[] { 3, 4 });
        CfdpHeader header = new CfdpHeader(true, false, false, false, 2, 2, 3, 4, 10);
        EofPacket p1 = new EofPacket(ConditionCode.FILE_CHECKSUM_FAILURE, 20, 100, tlv, header);
        ByteBuffer bb = ByteBuffer.allocate(100);
        p1.writeToBuffer(bb);

        bb.rewind();
        EofPacket p2 = (EofPacket) CfdpPacket.getCFDPPacket(bb);
        assertEquals(ConditionCode.FILE_CHECKSUM_FAILURE, p2.getConditionCode());
        assertEquals(tlv, p2.getFaultLocation());
    }

    @Test
    public void testShortHeader() {
        PduDecodingException e = null;
        byte[] b = StringConverter.hexStringToArray("00010A1");
        try {
            CfdpPacket.getCFDPPacket(ByteBuffer.wrap(b));
        } catch (PduDecodingException e1) {
            e = e1;
        }
        assertNotNull(e);
        assertArrayEquals(b, e.getData());

    }

    @Test
    public void testShortPDU() {
        PduDecodingException e = null;
        byte[] b = StringConverter.hexStringToArray("00010A130018000000010015048000000000000065fc");
        try {
            CfdpPacket.getCFDPPacket(ByteBuffer.wrap(b));
        } catch (PduDecodingException e1) {
            e = e1;
        }
        assertNotNull(e);
        assertArrayEquals(b, e.getData());
    }
}
