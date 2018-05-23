package org.yamcs.tctm;

import static org.junit.Assert.*;

import org.junit.Test;

public class TcpTcUplinkerTest {
    @Test
    public void testConfig() {
        TcpTcDataLink tcuplink = new TcpTcDataLink("testinst", "name0", "test_default");
        IssCommandPostprocessor icpp = (IssCommandPostprocessor) tcuplink.cmdPostProcessor;
        assertEquals(-1, icpp.getMiniminimumTcPacketLength());
        
        
        tcuplink = new TcpTcDataLink("testinst", "test1", "test48");
        icpp = (IssCommandPostprocessor) tcuplink.cmdPostProcessor;
        assertEquals(48, icpp.getMiniminimumTcPacketLength());
        
    }
}
