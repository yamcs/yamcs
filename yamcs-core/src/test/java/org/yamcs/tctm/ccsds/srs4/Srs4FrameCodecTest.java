package org.yamcs.tctm.ccsds.srs4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.TcTransferFrame;

public class Srs4FrameCodecTest {

    @Test
    public void testEthernetRoundTripWithUdpChecksum() throws Exception {
        byte[] ccsds = new byte[] { 1, 2, 3, 4, 5, 6, 7 };
        var encoder = new Srs4TcFrameEncapsulator(tcConfig(false, true, true));
        encoder.validate(100, List.of(3));

        byte[] encoded = encoder.encapsulate(frame(ccsds, 3, null));
        assertEquals(4 + 28 + ccsds.length, encoded.length);
        assertEquals(1, (encoded[0] >>> 3) & 1);
        assertEquals(encoded.length - 2, ((encoded[0] & 0x7) << 8) | (encoded[1] & 0xFF));
        assertEquals(0x1234, ((encoded[2] & 0xFF) << 8) | (encoded[3] & 0xFF));
        assertEquals(0x45, encoded[4] & 0xFF);

        var decoder = new Srs4TmFrameDecapsulator(tmConfig(false, true, true));
        decoder.validate(100, List.of(3));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertEquals(3, decoded.expectedVirtualChannelId());
        assertArrayEquals(ccsds,
                java.util.Arrays.copyOfRange(decoded.data(), decoded.offset(), decoded.offset() + decoded.length()));
    }

    @Test
    public void testCspRoundTrip() throws Exception {
        byte[] ccsds = new byte[] { 9, 8, 7, 6 };
        var encoder = new Srs4TcFrameEncapsulator(tcConfig(true, false, false));
        byte[] encoded = encoder.encapsulate(frame(ccsds, 3, null));

        assertEquals(4 + 4 + ccsds.length, encoded.length);
        assertEquals(0, (encoded[0] >>> 3) & 1);

        var decoder = new Srs4TmFrameDecapsulator(tmConfig(true, false, false));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertEquals(3, decoded.expectedVirtualChannelId());
        assertArrayEquals(ccsds,
                java.util.Arrays.copyOfRange(decoded.data(), decoded.offset(), decoded.offset() + decoded.length()));
    }

    @Test
    public void testDualFlowUsesCommandOption() throws Exception {
        var encoder = new Srs4TcFrameEncapsulator(tcConfig(true, true, false));
        PreparedCommand command = new PreparedCommand(new byte[0]);
        command.setAttribute(Srs4TcFrameEncapsulator.OPTION_USE_CAN.getId(), true);

        byte[] encoded = encoder.encapsulate(frame(new byte[] { 1 }, 3, command));
        assertEquals(0, (encoded[0] >>> 3) & 1);

        command.setAttribute(Srs4TcFrameEncapsulator.OPTION_USE_CAN.getId(), false);
        encoded = encoder.encapsulate(frame(new byte[] { 1 }, 3, command));
        assertEquals(1, (encoded[0] >>> 3) & 1);
    }

    @Test
    public void testRejectsRadioIdAndLengthMismatch() {
        var encoder = new Srs4TcFrameEncapsulator(tcConfig(true, false, false));
        byte[] encoded = encoder.encapsulate(frame(new byte[] { 1, 2 }, 3, null));
        var decoder = new Srs4TmFrameDecapsulator(tmConfig(true, false, false));

        encoded[3] ^= 1;
        assertThrows(TcTmException.class, () -> decoder.decapsulate(encoded, 0, encoded.length));
        encoded[3] ^= 1;
        encoded[1]--;
        assertThrows(TcTmException.class, () -> decoder.decapsulate(encoded, 0, encoded.length));
    }

    @Test
    public void testRejectsMissingRadioAndLengthOverflow() {
        Map<String, Object> srs4 = base(true, false, false, true);
        srs4.remove("radio");
        assertThrows(ConfigurationException.class,
                () -> new Srs4TcFrameEncapsulator(YConfiguration.wrap(Map.of("srs4", srs4))));

        var encoder = new Srs4TcFrameEncapsulator(tcConfig(false, true, false));
        assertThrows(ConfigurationException.class, () -> encoder.validate(2018, List.of(3)));
    }

    private static TcTransferFrame frame(byte[] data, int vcId, PreparedCommand command) {
        TcTransferFrame frame = new TcTransferFrame(data, 1, vcId, false);
        if (command != null) {
            frame.setCommands(List.of(command));
        }
        return frame;
    }

    private static YConfiguration tcConfig(boolean csp, boolean ethernet, boolean udpChecksum) {
        Map<String, Object> srs4 = base(csp, ethernet, udpChecksum, true);
        return YConfiguration.wrap(Map.of("srs4", srs4));
    }

    private static YConfiguration tmConfig(boolean csp, boolean ethernet, boolean udpChecksum) {
        Map<String, Object> srs4 = base(csp, ethernet, udpChecksum, false);
        return YConfiguration.wrap(Map.of("srs4", srs4));
    }

    private static Map<String, Object> base(boolean csp, boolean ethernet, boolean udpChecksum, boolean tc) {
        Map<String, Object> srs4 = new LinkedHashMap<>();
        srs4.put("radio", Map.of("enabled", true, "spacecraftId", 0x1234));
        if (csp) {
            Map<String, Object> cspConfig = new LinkedHashMap<>();
            cspConfig.put("enabled", true);
            cspConfig.put(tc ? "sourceAddress" : "destinationAddress", tc ? 1 : 2);
            cspConfig.put(tc ? "sourcePort" : "destinationPort", tc ? 10 : 20);
            cspConfig.put("priority", 2);
            cspConfig.put("rdp", true);
            srs4.put("csp", cspConfig);
        }
        if (ethernet) {
            Map<String, Object> ipConfig = new LinkedHashMap<>();
            ipConfig.put("enabled", true);
            ipConfig.put(tc ? "sourceAddress" : "destinationAddress", tc ? "10.0.0.1" : "10.0.0.2");
            ipConfig.put(tc ? "sourcePort" : "destinationPort", tc ? 1000 : 2000);
            ipConfig.put("ttl", 32);
            ipConfig.put("calculateUdpChecksum", udpChecksum);
            srs4.put("ipv4Udp", ipConfig);
        }

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("vcId", 3);
        if (csp) {
            route.put("csp", Map.of(tc ? "destinationAddress" : "sourceAddress", tc ? 2 : 1,
                    tc ? "destinationPort" : "sourcePort", tc ? 20 : 10));
        }
        if (ethernet) {
            route.put("ipv4Udp", Map.of(tc ? "destinationAddress" : "sourceAddress",
                    tc ? "10.0.0.2" : "10.0.0.1", tc ? "destinationPort" : "sourcePort", tc ? 2000 : 1000));
        }
        srs4.put("virtualChannels", List.of(route));
        return srs4;
    }
}
