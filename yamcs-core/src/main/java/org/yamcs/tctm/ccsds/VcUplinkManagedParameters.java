package org.yamcs.tctm.ccsds;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Stores configuration related to Virtual Channels for uplink
 * 
 * @author nm
 *
 */
public class VcUplinkManagedParameters {
    protected int vcId;

    String packetPostprocessorClassName;
    YConfiguration packetPostprocessorArgs;
    protected int priority;

    final YConfiguration config;
    /**
     * The Security Parameter Index used on this channel
     */
    short encryptionSpi;

    /**
     * Mask to authenticate additional header data
     */
    byte[] authMask;

    public VcUplinkManagedParameters(int vcId) {
        this.vcId = vcId;
        this.config = null;
    }

    public VcUplinkManagedParameters(YConfiguration config, UplinkManagedParameters params) {
        this.config = config;
        this.vcId = config.getInt("vcId");
        this.priority = config.getInt("priority", 1);
        if (config.containsKey("encryptionSpi")) {
            encryptionSpi = (short) config.getInt("encryptionSpi");
            // If there is no security association for this SPI, it's a configuration error.
            if (!params.sdlsSecurityAssociations.containsKey(encryptionSpi)) {
                throw new ConfigurationException("Encryption SPI " + encryptionSpi
                        + " configured for vcId "
                        + vcId + " is not configured for link " + config.getString("linkName"));
            }
        }
    }

    protected void parsePacketConfig() {
        packetPostprocessorClassName = config.getString("packetPostrocessorClassName");
        if (config.containsKey("packetPreprocessorArgs")) {
            packetPostprocessorArgs = config.getConfig("packetPostprocessorArgs");
        }
    }

    public int getPriority() {
        return priority;
    }

    public int getVirtualChannelId() {
        return vcId;
    }
}
