package org.yamcs.tctm;

import static org.junit.Assert.*;

import org.junit.Test;

public class TcpTcUplinkerTest {
    @Test
    public void testConfig() {
        TcpTcUplinker tcuplink = new TcpTcUplinker("testinst", "name0", "test_default");
        assertEquals(-1, tcuplink.getMiniminimumTcPacketLength());
        
        
        tcuplink = new TcpTcUplinker("testinst", "test1", "test48");
        assertEquals(48, tcuplink.getMiniminimumTcPacketLength());
        
    }
}
