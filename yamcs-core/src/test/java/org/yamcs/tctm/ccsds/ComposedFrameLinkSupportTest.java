package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class ComposedFrameLinkSupportTest {

    @Test
    public void testLoadsAndCombinesTypedProtocolComponents() {
        Map<String, Object> protocolArgs = Map.of(
                "spacecraftId", 1,
                "maxFrameLength", 128,
                "virtualChannels", List.of(Map.of("vcId", 0, "service", "PACKET")));
        Map<String, Object> securityArgs = Map.of(
                "encryption", List.of(Map.of("spi", 4, "class", "example.Factory", "args", Map.of())),
                "virtualChannels", List.of(Map.of("vcId", 0, "encryptionSpi", 4)));
        var components = YConfiguration.wrap(Map.of(
                "protocol", Map.of("class", CcsdsTcFrameProtocol.class.getName(), "args", protocolArgs),
                "protocolSecurity", Map.of("class", CcsdsTcSdlsConfiguration.class.getName(),
                        "args", securityArgs)));

        YConfiguration result = ComposedFrameLinkSupport.protocolConfiguration("test", "tc", components, true);
        assertEquals(4, result.getConfigList("virtualChannels").get(0).getInt("encryptionSpi"));
        assertEquals(4, result.getConfigList("encryption").get(0).getInt("spi"));
    }

    @Test
    public void testRejectsWrongProtocolDirection() {
        var components = YConfiguration.wrap(Map.of(
                "protocol", Map.of("class", CcsdsTmFrameProtocol.class.getName(), "args", Map.of())));
        assertThrows(ConfigurationException.class,
                () -> ComposedFrameLinkSupport.protocolConfiguration("test", "tc", components, true));
    }

    @Test
    public void testRejectsSdlsHiddenInsideProtocolDefinition() {
        var components = YConfiguration.wrap(Map.of(
                "protocol", Map.of(
                        "class", CcsdsTcFrameProtocol.class.getName(),
                        "args", Map.of(
                                "encryption", List.of(Map.of("spi", 1)),
                                "virtualChannels", List.of(Map.of("vcId", 0))))));
        assertThrows(ConfigurationException.class,
                () -> ComposedFrameLinkSupport.protocolConfiguration("test", "tc", components, true));
    }

    @Test
    public void testTcSdlsProjectionDoesNotMutateProtocolConfig() {
        var protocol = YConfiguration.wrap(Map.of(
                "spacecraftId", 1,
                "maxFrameLength", 128,
                "virtualChannels", List.of(Map.of("vcId", 0, "service", "PACKET"))));
        var security = new CcsdsTcSdlsConfiguration();
        security.init("test", "tc", YConfiguration.wrap(Map.of(
                "encryption", List.of(Map.of("spi", 4, "class", "example.Factory", "args", Map.of())),
                "virtualChannels", List.of(Map.of("vcId", 0, "encryptionSpi", 4)))));

        YConfiguration result = security.applyTo(protocol);
        assertEquals(4, result.getConfigList("virtualChannels").get(0).getInt("encryptionSpi"));
        assertEquals(4, result.getConfigList("encryption").get(0).getInt("spi"));
        assertFalse(protocol.containsKey("encryption"));
        assertFalse(protocol.getConfigList("virtualChannels").get(0).containsKey("encryptionSpi"));
    }

    @Test
    public void testSdlsRejectsUnknownVc() {
        var protocol = YConfiguration.wrap(Map.of("virtualChannels", List.of(Map.of("vcId", 0))));
        var security = new CcsdsTmSdlsConfiguration();
        security.init("test", "tm", YConfiguration.wrap(Map.of(
                "virtualChannels", List.of(Map.of("vcId", 2, "encryptionSpis", List.of(1))))));
        assertThrows(ConfigurationException.class, () -> security.applyTo(protocol));
    }

    @Test
    public void testDecodedFrameLengthIncludesOverheadOnBothBounds() {
        ComposedFrameLinkSupport.validateDecodedFrameLength(-1, 100, 200, 32);
        ComposedFrameLinkSupport.validateDecodedFrameLength(132, 100, 200, 32);
        ComposedFrameLinkSupport.validateDecodedFrameLength(232, 100, 200, 32);

        assertThrows(ConfigurationException.class,
                () -> ComposedFrameLinkSupport.validateDecodedFrameLength(131, 100, 200, 32));
        assertThrows(ConfigurationException.class,
                () -> ComposedFrameLinkSupport.validateDecodedFrameLength(233, 100, 200, 32));
        assertThrows(ConfigurationException.class,
                () -> ComposedFrameLinkSupport.validateDecodedFrameLength(0, 100, 200, 32));
    }

    @Test
    public void testFixedInnerFrameRequiresExactDecodedLength() {
        ComposedFrameLinkSupport.validateDecodedFrameLength(143, 111, 111, 32);
        assertThrows(ConfigurationException.class,
                () -> ComposedFrameLinkSupport.validateDecodedFrameLength(142, 111, 111, 32));
        assertThrows(ConfigurationException.class,
                () -> ComposedFrameLinkSupport.validateDecodedFrameLength(144, 111, 111, 32));
    }
}
