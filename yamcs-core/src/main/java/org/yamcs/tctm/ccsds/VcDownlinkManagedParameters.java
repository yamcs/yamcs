package org.yamcs.tctm.ccsds;

import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Stores configuration related to Virtual Channels
 *
 */
public class VcDownlinkManagedParameters {
    protected int vcId;
    // if set to true, the encapsulation packets sent to the preprocessor will be without the encapsulation header(CCSDS
    // 133.1-B-2)
    boolean stripEncapsulationHeader;

    // if service = M_PDU
    int maxPacketLength;
    String packetPreprocessorClassName;
    YConfiguration packetPreprocessorArgs;
    final YConfiguration config;
    protected String vcaHandlerClassName;

    /**
     * The Security Parameter Index used on this channel
     * <p>
     * If length higher than 0, it means this channel is encrypted
     */
    short[] encryptionSpis = new short[0];

    public VcDownlinkManagedParameters(int vcId) {
        this.vcId = vcId;
        this.config = null;
    }

    public VcDownlinkManagedParameters(YConfiguration config, DownlinkManagedParameters params) {
        this.config = config;
        this.vcId = config.getInt("vcId");
        if (config.containsKey("encryptionSpis")) {
            List<Integer> spis = config.getList("encryptionSpis");
            if (spis.isEmpty()) {
                throw new ConfigurationException("List of encryption SPIs should have at least one element, but is " +
                        "empty for vcId " + vcId + " link " + params.linkName);
            }
            encryptionSpis = new short[spis.size()];
            for (int i = 0; i < spis.size(); ++i) {
                short currentSpi = spis.get(i).shortValue();
                // If there is no security association for this SPI, it's a configuration error.
                if (!params.sdlsSecurityAssociations.containsKey(currentSpi)) {
                    throw new ConfigurationException("Encryption SPI " + currentSpi
                            + " configured for vcId "
                            + vcId + " is not configured for link " + params.linkName);
                }

                encryptionSpis[i] = currentSpi;
            }
        }
    }

    protected void parsePacketConfig() {
        maxPacketLength = config.getInt("maxPacketLength", 65536);
        if (maxPacketLength < 7) {
            throw new ConfigurationException("invalid maxPacketLength: " + maxPacketLength);
        }

        packetPreprocessorClassName = config.getString("packetPreprocessorClassName");
        if (config.containsKey("packetPreprocessorArgs")) {
            packetPreprocessorArgs = config.getConfig("packetPreprocessorArgs");
        }
        stripEncapsulationHeader = config.getBoolean("stripEncapsulationHeader", false);
    }

    protected void parseVcaConfig() {
        this.vcaHandlerClassName = config.getString("vcaHandlerClassName");
    }
}