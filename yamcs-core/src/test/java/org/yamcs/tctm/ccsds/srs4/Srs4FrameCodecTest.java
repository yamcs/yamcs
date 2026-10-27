package org.yamcs.tctm.ccsds.srs4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.AbstractTcFrameLink;
import org.yamcs.tctm.ccsds.AbstractTmFrameLink;
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
        decoder.validate(ccsds.length, List.of(3));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertIterableEquals(List.of(3), decoded.expectedVirtualChannelIds());
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
        assertEquals(0, encoded[4] & 0xC0);
        assertEquals(0, encoded[6] & 0x3F);
        assertEquals(0, encoded[7] & 0x0F);

        var decoder = new Srs4TmFrameDecapsulator(tmConfig(true, false, false));
        decoder.validate(ccsds.length, List.of(3));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertIterableEquals(List.of(3), decoded.expectedVirtualChannelIds());
        assertArrayEquals(ccsds,
                java.util.Arrays.copyOfRange(decoded.data(), decoded.offset(), decoded.offset() + decoded.length()));
    }

    @Test
    public void testCspTmIgnoresSourcePortPriorityAndFlags() throws Exception {
        byte[] ccsds = new byte[] { 9, 8, 7, 6 };
        var encoder = new Srs4TcFrameEncapsulator(tcConfig(true, false, false));
        byte[] encoded = encoder.encapsulate(frame(ccsds, 3, null));

        encoded[4] |= (byte) 0xC0; // priority
        encoded[6] = (byte) ((encoded[6] & 0xC0) | 0x3F); // source port
        encoded[7] |= 0x0F; // flags

        var decoder = new Srs4TmFrameDecapsulator(tmConfig(true, false, false));
        decoder.validate(ccsds.length, List.of(3));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertIterableEquals(List.of(3), decoded.expectedVirtualChannelIds());
    }

    @Test
    public void testTmCspSourceAddressMapsToMultipleVcIds() throws Exception {
        Map<String, Object> srs4 = base(true, false, false, false);
        srs4.put("virtualChannels", List.of(Map.of(
                "vcIds", List.of(3, 4),
                "csp", List.of(Map.of("sourceAddress", 1)))));

        var encoder = new Srs4TcFrameEncapsulator(tcConfig(true, false, false));
        byte[] encoded = encoder.encapsulate(frame(new byte[] { 9, 8, 7, 6 }, 3, null));
        var decoder = new Srs4TmFrameDecapsulator(YConfiguration.wrap(Map.of("srs4", srs4)));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertIterableEquals(List.of(3, 4), decoded.expectedVirtualChannelIds());
    }

    @Test
    public void testTmIpv4EndpointMapsToMultipleVcIds() throws Exception {
        Map<String, Object> srs4 = base(false, true, false, false);
        srs4.put("virtualChannels", List.of(Map.of(
                "vcIds", List.of(3, 4),
                "ipv4Udp", List.of(Map.of("sourceAddress", "10.0.0.1", "sourcePort", 1000)))));

        var encoder = new Srs4TcFrameEncapsulator(tcConfig(false, true, false));
        byte[] encoded = encoder.encapsulate(frame(new byte[] { 9, 8, 7, 6 }, 3, null));
        var decoder = new Srs4TmFrameDecapsulator(YConfiguration.wrap(Map.of("srs4", srs4)));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertIterableEquals(List.of(3, 4), decoded.expectedVirtualChannelIds());
    }

    @Test
    public void testTmVcIdHasMultipleIpv4SourceEndpoints() throws Exception {
        Map<String, Object> srs4 = base(false, true, false, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> route = (Map<String, Object>) ((List<?>) srs4.get("virtualChannels")).get(0);
        route.put("ipv4Udp", List.of(
                Map.of("sourceAddress", "10.0.0.1", "sourcePort", 1000),
                Map.of("sourceAddress", "10.0.0.3", "sourcePort", 1001)));

        var encoder = new Srs4TcFrameEncapsulator(tcConfig(false, true, false));
        byte[] encoded = encoder.encapsulate(frame(new byte[] { 9, 8, 7, 6 }, 3, null));
        var decoder = new Srs4TmFrameDecapsulator(YConfiguration.wrap(Map.of("srs4", srs4)));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertIterableEquals(List.of(3), decoded.expectedVirtualChannelIds());
    }

    @Test
    public void testTmVcIdCanAppearInMultipleRouteGroups() throws Exception {
        Map<String, Object> srs4 = base(false, true, false, false);
        srs4.put("virtualChannels", List.of(
                Map.of("vcIds", List.of(3, 4),
                        "ipv4Udp", List.of(Map.of("sourceAddress", "10.0.0.1", "sourcePort", 1000))),
                Map.of("vcIds", List.of(3),
                        "ipv4Udp", List.of(Map.of("sourceAddress", "10.0.0.3", "sourcePort", 1001)))));

        var encoder = new Srs4TcFrameEncapsulator(tcConfig(false, true, false));
        byte[] encoded = encoder.encapsulate(frame(new byte[] { 9, 8, 7, 6 }, 3, null));
        var decoder = new Srs4TmFrameDecapsulator(YConfiguration.wrap(Map.of("srs4", srs4)));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertIterableEquals(List.of(3, 4), decoded.expectedVirtualChannelIds());
    }

    @Test
    public void testRejectsEmptyTmVcIds() {
        Map<String, Object> srs4 = base(true, false, false, false);
        srs4.put("virtualChannels", List.of(Map.of(
                "vcIds", List.of(),
                "csp", List.of(Map.of("sourceAddress", 1)))));

        assertThrows(ConfigurationException.class,
                () -> new Srs4TmFrameDecapsulator(YConfiguration.wrap(Map.of("srs4", srs4))));
    }

    @Test
    public void testIpv4TmAcceptsFragmentFields() throws Exception {
        byte[] ccsds = new byte[] { 1, 2, 3, 4 };
        var encoder = new Srs4TcFrameEncapsulator(tcConfig(false, true, false));
        byte[] encoded = encoder.encapsulate(frame(ccsds, 3, null));
        int ipOffset = Srs4RadioHeaderCodec.HEADER_LENGTH;
        encoded[ipOffset + 6] = 0x20;
        encoded[ipOffset + 7] = 0x01;
        updateIpv4HeaderChecksum(encoded, ipOffset);

        var decoder = new Srs4TmFrameDecapsulator(tmConfig(false, true, false));
        decoder.validate(ccsds.length, List.of(3));
        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertIterableEquals(List.of(3), decoded.expectedVirtualChannelIds());
    }

    @Test
    public void testSrs4AcceptsVariableLengthInnerFrames() throws Exception {
        byte[] ccsds = new byte[] { 1, 2, 3, 4, 5 };
        var encoder = new Srs4TcFrameEncapsulator(tcConfig(false, true, false));
        byte[] encoded = encoder.encapsulate(frame(ccsds, 3, null));
        var decoder = new Srs4TmFrameDecapsulator(tmConfig(false, true, false));

        var decoded = decoder.decapsulate(encoded, 0, encoded.length);
        assertEquals(ccsds.length, decoded.length());
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

    @Test
    public void testTcLinkSpecValidatesSrs4Arguments() throws ValidationException {
        Spec spec = AbstractTcFrameLink.addDefaultOptions(new Spec());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("srs4", base(true, true, false, true));
        Map<String, Object> link = Map.of("frameEncapsulation", Map.of(
                "class", Srs4ConfigSpec.TC_CLASS,
                "args", args));

        assertDoesNotThrow(() -> spec.validate(link));
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("frameEncapsulation", Map.of(
                "class", Srs4ConfigSpec.TC_CLASS,
                "args", Map.of("srs4", Map.of("radio", Map.of("spacecraftId", "not-an-integer")))))));
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("frameEncapsulation", Map.of(
                "class", Srs4ConfigSpec.TC_CLASS))));
        Map<String, Object> legacySrs4 = base(true, false, false, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> legacyCsp = (Map<String, Object>) legacySrs4.get("csp");
        legacyCsp.put("sourcePort", 10);
        legacyCsp.put("priority", 1);
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("frameEncapsulation", Map.of(
                "class", Srs4ConfigSpec.TC_CLASS,
                "args", Map.of("srs4", legacySrs4)))));
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("frameEncapsulation", Map.of(
                "class", Srs4ConfigSpec.TC_CLASS,
                "args", Map.of("srs4", tcRouteWithPluralVcIds())))));
        assertDoesNotThrow(() -> spec.validate(Map.of("frameEncapsulation", Map.of(
                "class", "example.CustomEncapsulator",
                "args", Map.of("customOption", "accepted")))));
    }

    @Test
    public void testTmLinkSpecValidatesSrs4Arguments() throws ValidationException {
        Spec spec = AbstractTmFrameLink.addDefaultOptions(new Spec());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("srs4", base(true, true, false, false));
        Map<String, Object> link = Map.of("frameDecapsulation", Map.of(
                "class", Srs4ConfigSpec.TM_CLASS,
                "args", args));

        assertDoesNotThrow(() -> spec.validate(link));
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("frameDecapsulation", Map.of(
                "class", Srs4ConfigSpec.TM_CLASS,
                "args", Map.of("srs4", Map.of("radio", Map.of("spacecraftId", "not-an-integer")))))));
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("frameDecapsulation", Map.of(
                "class", Srs4ConfigSpec.TM_CLASS))));
        assertThrows(ValidationException.class, () -> spec.validate(Map.of("frameDecapsulation", Map.of(
                "class", Srs4ConfigSpec.TM_CLASS,
                "args", Map.of("srs4", tmRouteWithSingularVcId())))));
        assertDoesNotThrow(() -> spec.validate(Map.of("frameDecapsulation", Map.of(
                "class", "example.CustomDecapsulator",
                "args", Map.of("customOption", "accepted")))));
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
            if (!tc) {
                cspConfig.put("destinationPort", 20);
            }
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
        route.put(tc ? "vcId" : "vcIds", tc ? 3 : List.of(3));
        if (csp) {
            Map<String, Object> endpoint = tc ? Map.of("destinationAddress", 2, "destinationPort", 20)
                    : Map.of("sourceAddress", 1);
            route.put("csp", tc ? endpoint : List.of(endpoint));
        }
        if (ethernet) {
            Map<String, Object> endpoint = Map.of(tc ? "destinationAddress" : "sourceAddress",
                    tc ? "10.0.0.2" : "10.0.0.1", tc ? "destinationPort" : "sourcePort", tc ? 2000 : 1000);
            route.put("ipv4Udp", tc ? endpoint : List.of(endpoint));
        }
        srs4.put("virtualChannels", List.of(route));
        return srs4;
    }

    private static Map<String, Object> tmRouteWithSingularVcId() {
        Map<String, Object> srs4 = base(true, false, false, false);
        srs4.put("virtualChannels", List.of(Map.of(
                "vcId", 3,
                "csp", List.of(Map.of("sourceAddress", 1)))));
        return srs4;
    }

    private static Map<String, Object> tcRouteWithPluralVcIds() {
        Map<String, Object> srs4 = base(true, false, false, true);
        srs4.put("virtualChannels", List.of(Map.of(
                "vcIds", List.of(3),
                "csp", Map.of("destinationAddress", 2, "destinationPort", 20))));
        return srs4;
    }

    private static void updateIpv4HeaderChecksum(byte[] data, int offset) {
        data[offset + 10] = 0;
        data[offset + 11] = 0;
        int sum = 0;
        for (int i = 0; i < Srs4Ipv4UdpHeaderCodec.IPV4_HEADER_LENGTH; i += 2) {
            sum += ((data[offset + i] & 0xFF) << 8) | (data[offset + i + 1] & 0xFF);
        }
        while ((sum >>> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >>> 16);
        }
        int checksum = (~sum) & 0xFFFF;
        data[offset + 10] = (byte) (checksum >>> 8);
        data[offset + 11] = (byte) checksum;
    }
}
