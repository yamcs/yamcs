package org.yamcs.tctm;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class TcpTcUplinkerTest {

    @Test
    public void testConfig1() {
        Map<String, Object> config = new HashMap<>();
        config.put("host", "localhost");
        config.put("port", 10025);
        TcpTcDataLink tcuplink = new TcpTcDataLink("testinst", "name0", config);
        IssCommandPostprocessor icpp = (IssCommandPostprocessor) tcuplink.cmdPostProcessor;
        assertEquals(-1, icpp.getMiniminimumTcPacketLength());
    }

    @Test
    public void testConfig2() {
        Map<String, Object> config = new HashMap<>();
        config.put("host", "localhost");
        config.put("port", 10025);

        Map<String, Object> postProcessorArgs = new HashMap<>();
        postProcessorArgs.put("minimumTcPacketLength", 48);
        config.put("commandPostprocessorArgs", postProcessorArgs);

        TcpTcDataLink tcuplink = new TcpTcDataLink("testinst", "test1", config);
        IssCommandPostprocessor icpp = (IssCommandPostprocessor) tcuplink.cmdPostProcessor;
        assertEquals(48, icpp.getMiniminimumTcPacketLength());
    }
}
