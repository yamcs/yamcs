package org.yamcs.tctm.ccsds;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.DataLinkComponentLoader;

final class ComposedFrameLinkSupport {
    private static final Set<String> SLOTS = Set.of(
            "protocol", "protocolSecurity", "outerFrame", "channelCoding", "transport");

    private ComposedFrameLinkSupport() {
    }

    static YConfiguration components(YConfiguration linkConfig) {
        if (!linkConfig.containsKey("components")) {
            throw new ConfigurationException("Composed frame link requires components");
        }
        YConfiguration components = linkConfig.getConfig("components");
        for (String key : components.toMap().keySet()) {
            if (!SLOTS.contains(key)) {
                throw new ConfigurationException("Unsupported composed frame link slot '" + key + "'");
            }
        }
        if (!components.containsKey("protocol") || !components.containsKey("transport")) {
            throw new ConfigurationException("Composed frame link requires protocol and transport components");
        }
        return components;
    }

    static YConfiguration protocolConfiguration(String instance, String linkName, YConfiguration components,
            boolean telecommand) {
        FrameProtocolConfiguration protocol = DataLinkComponentLoader.load(components.getConfig("protocol"),
                FrameProtocolConfiguration.class, instance, linkName, "protocol");
        if (protocol.isTelecommand() != telecommand) {
            throw new ConfigurationException("Protocol component direction does not match composed link direction");
        }

        YConfiguration config = protocol.configuration();
        validateSecuritySeparation(config, telecommand);
        if (components.containsKey("protocolSecurity")) {
            CcsdsFrameSecurityConfiguration security = DataLinkComponentLoader.load(
                    components.getConfig("protocolSecurity"), CcsdsFrameSecurityConfiguration.class,
                    instance, linkName, "protocolSecurity");
            if (security.isTelecommand() != telecommand) {
                throw new ConfigurationException(
                        "Protocol security component direction does not match composed link direction");
            }
            config = security.applyTo(config);
        }

        Map<String, Object> flattened = new LinkedHashMap<>(config.toMap());
        if (components.containsKey("outerFrame")) {
            flattened.put(telecommand ? "frameEncapsulation" : "frameDecapsulation",
                    components.getConfig("outerFrame").toMap());
        }
        YConfiguration result = YConfiguration.wrap(flattened);
        try {
            return telecommand
                    ? AbstractTcFrameLink.addDefaultOptions(new org.yamcs.Spec()).validate(result)
                    : AbstractTmFrameLink.addDefaultOptions(new org.yamcs.Spec()).validate(result);
        } catch (ValidationException e) {
            throw new ConfigurationException("Invalid CCSDS protocol composition: " + e.getMessage(), e);
        }
    }

    private static void validateSecuritySeparation(YConfiguration protocol, boolean telecommand) {
        if (protocol.containsKey("encryption")) {
            throw new ConfigurationException(
                    "Composed CCSDS protocol must configure encryption through protocolSecurity");
        }
        if (protocol.containsKey("virtualChannels")) {
            String selectionKey = telecommand ? "encryptionSpi" : "encryptionSpis";
            for (YConfiguration vc : protocol.getConfigList("virtualChannels")) {
                if (vc.containsKey(selectionKey)) {
                    throw new ConfigurationException("Composed CCSDS protocol vcId " + vc.getInt("vcId")
                            + " must configure " + selectionKey + " through protocolSecurity");
                }
            }
        }
    }

    static void validateDecodedFrameLength(int decodedLength, int minimumInnerLength, int maximumInnerLength,
            int decapsulationOverhead) {
        validateDecodedFrameLength("TM channel decoder", decodedLength, minimumInnerLength, maximumInnerLength,
                decapsulationOverhead);
    }

    static void validateDecodedFrameLength(String decoderName, int decodedLength, int minimumInnerLength,
            int maximumInnerLength, int decapsulationOverhead) {
        if (decodedLength == -1) {
            return;
        }
        if (decodedLength <= 0) {
            throw new ConfigurationException(decoderName + " returned invalid decoded frame length "
                    + decodedLength + "; expected a positive value or -1");
        }
        int minimum = minimumInnerLength + decapsulationOverhead;
        int maximum = maximumInnerLength + decapsulationOverhead;
        if (decodedLength < minimum || decodedLength > maximum) {
            throw new ConfigurationException(decoderName + " output frame length " + decodedLength
                    + " does not match the defined frame length including decapsulation overhead "
                    + (minimum == maximum ? minimum : "[" + minimum + ", " + maximum + "]"));
        }
    }
}
