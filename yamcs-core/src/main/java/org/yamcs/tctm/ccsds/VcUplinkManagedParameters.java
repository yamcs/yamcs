package org.yamcs.tctm.ccsds;

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

    public VcUplinkManagedParameters(int vcId) {
        this.vcId = vcId;
        this.config = null;
    }

    public VcUplinkManagedParameters(YConfiguration config) {
        this.config = config;
        this.vcId = config.getInt("vcId");
        this.priority = config.getInt("priority", 1);
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
