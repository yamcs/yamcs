package org.yamcs.tctm;

import static org.junit.Assert.*;

import org.junit.Test;

public class TcpTcUplinkerTest {
    @Test
    public void testConfig() {
        TcpTcDataLink tcuplink = new TcpTcDataLink("testinst", "name0", "test_default");
        assertEquals(-1, tcuplink.getMiniminimumTcPacketLength());
        
        
        tcuplink = new TcpTcDataLink("testinst", "test1", "test48");
        assertEquals(48, tcuplink.getMiniminimumTcPacketLength());
        
    }
}
