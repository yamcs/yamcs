package org.yamcs.tctm;

import static org.junit.Assert.*;

import org.junit.Test;

public class TcpTcUplinkerTest {
    @Test
    public void testConfig() {
        TcpTcUplinker tcuplink = new TcpTcUplinker("testinst", "name0", "test_default");
        assertEquals(48, tcuplink.getMiniminimumTcPacketLength());
        
        
        tcuplink = new TcpTcUplinker("testinst", "test1", "test0");
        assertEquals(0, tcuplink.getMiniminimumTcPacketLength());
        
    }
}
