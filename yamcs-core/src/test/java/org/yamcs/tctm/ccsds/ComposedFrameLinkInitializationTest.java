package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.TcFrameTransport;
import org.yamcs.tctm.TmFrameTransport;
import org.yamcs.utils.TimeEncoding;

public class ComposedFrameLinkInitializationTest {

    @BeforeAll
    public static void setup() {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(true);
    }

    @Test
    public void testInitializesTcProtocolAndVirtualChannel() {
        Map<String, Object> protocol = Map.of(
                "spacecraftId", 1,
                "maxFrameLength", 128,
                "errorDetection", "NONE",
                "virtualChannels", List.of(Map.of(
                        "vcId", 0,
                        "service", "PACKET",
                        "commandPostprocessorClassName", "org.yamcs.tctm.GenericCommandPostprocessor")));
        var config = YConfiguration.wrap(Map.of("components", Map.of(
                "protocol", definition(CcsdsTcFrameProtocol.class, protocol),
                "transport", definition(FakeTcTransport.class, Map.of()))));

        var link = new ComposedTcFrameLink();
        link.init("test", "tc", config);
        assertEquals(1, link.getSubLinks().size());
    }

    @Test
    public void testInitializesTmProtocolAndVirtualChannel() {
        Map<String, Object> protocol = Map.of(
                "frameType", "TM",
                "spacecraftId", 1,
                "frameLength", 32,
                "errorDetection", "NONE",
                "virtualChannels", List.of(Map.of(
                        "vcId", 0,
                        "service", "PACKET",
                        "packetPreprocessorClassName", "org.yamcs.tctm.GenericPacketPreprocessor",
                        "packetPreprocessorArgs", Map.of("timestampOffset", -1, "seqCountOffset", -1))));
        var config = YConfiguration.wrap(Map.of("components", Map.of(
                "protocol", definition(CcsdsTmFrameProtocol.class, protocol),
                "transport", definition(FakeTmTransport.class, Map.of()))));

        var link = new ComposedTmFrameLink();
        link.init("test", "tm", config);
        assertEquals(1, link.getSubLinks().size());
    }

    private static Map<String, Object> definition(Class<?> type, Map<String, Object> args) {
        return Map.of("class", type.getName(), "args", args);
    }

    public static class FakeTcTransport implements TcFrameTransport {
        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void send(byte[] data) throws IOException {
        }
    }

    public static class FakeTmTransport implements TmFrameTransport {
        @Override
        public void start(int maximumFrameLength, Receiver receiver) {
        }

        @Override
        public void stop() {
        }
    }
}
