package org.yamcs.cfdp;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.utils.StringConverter;

public class TestParsePacket {

    @Test
    public void testEofPDU() {
        byte[] b = StringConverter.hexStringToArray("00000A130018000000010015048000000000000065fc");
        EofPacket p = (EofPacket) CfdpPacket.getCFDPPacket(ByteBuffer.wrap(b));
        assertEquals(ConditionCode.INACTIVITY_DETECTED, p.getConditionCode());
    }
}
