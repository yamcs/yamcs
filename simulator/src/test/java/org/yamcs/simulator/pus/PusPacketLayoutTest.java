package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PusPacketLayoutTest {
    @Test
    void ordinaryPacketsReserveFourBytesAndExposeOnlyServiceData() {
        var tc = new PusTcPacket(16, 1, 11, 17, 1);
        tc.getUserDataBuffer().put((byte) 42);
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 42 }, Arrays.copyOfRange(tc.getBytes(), 11, 16));
        assertEquals(1, tc.getUserDataBuffer().remaining());
        assertEquals(11, tc.getAckFlags());

        var time = new PusTimeEncoding(46, -1, true, PusTimeEncoding.Epoch.UNIX);
        var tm = new PusTmPacket(16, 1, 17, 2, time);
        assertEquals(23, tm.getUserDataOffset());
        assertArrayEquals(new byte[4], Arrays.copyOfRange(tm.getBytes(), 19, 23));
        assertEquals(1, tm.getUserDataBuffer().remaining());
    }
}
