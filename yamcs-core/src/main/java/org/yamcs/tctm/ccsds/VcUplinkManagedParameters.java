package org.yamcs.tctm.ccsds;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Stores configuration related to Virtual Channels for uplink
 * 
 */
public abstract class VcUplinkManagedParameters<T extends UplinkTransferFrame> {
    protected int vcId;

    String packetPostprocessorClassName;
    YConfiguration packetPostprocessorArgs;
    protected int priority;

    final YConfiguration config;
    /**
     * The Security Parameter Index used on this channel
     */
    short encryptionSpi;


    public boolean multiplePacketsPerFrame;

    public boolean bdAbsolutePriority;

    // if not negative, it contains the default MAP_ID to be used for this virtual channel
    // if negative, this virtual channel does not use the MAP service
    protected byte mapId;

    boolean useCop1;

    String vcLinkName;

    public boolean isBdAbsolutePriority() {
        return bdAbsolutePriority;
    }

    public void setBdAbsolutePriority(boolean bdAbsolutePriority) {
        this.bdAbsolutePriority = bdAbsolutePriority;
    }

    public VcUplinkManagedParameters(YConfiguration config, UplinkManagedParameters<T> params) {
        this.config = config;
        this.vcId = config.getInt("vcId");
        this.priority = config.getInt("priority", 1);
        if (config.containsKey("encryptionSpi")) {
            encryptionSpi = (short) config.getInt("encryptionSpi");
            // If there is no security association for this SPI, it's a configuration error.
            if (!params.sdlsSecurityAssociations.containsKey(encryptionSpi)) {
                throw new ConfigurationException("Encryption SPI " + encryptionSpi
                        + " configured for vcId "
                        + vcId + " is not configured for link " + params.linkName);
            }
        }
        this.bdAbsolutePriority = config.getBoolean("bdAbsolutePriority", false);
        this.multiplePacketsPerFrame = config.getBoolean("multiplePacketsPerFrame", true);

        this.mapId = (byte) config.getInt("mapId", -1);
        if (mapId < -1 || mapId > 15) {
            throw new ConfigurationException("Invalid mapId " + mapId
                    + ". It has to be either -1 (meaning that the MAP service is not used) or between 0 and 15");
        }

        this.useCop1 = config.getBoolean("useCop1", false);

        vcLinkName = config.getString("linkName", "vc" + vcId);
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

    public abstract int getMaxFrameLength();

    public abstract UplinkFrameFactory<T> getFrameFactory();

}