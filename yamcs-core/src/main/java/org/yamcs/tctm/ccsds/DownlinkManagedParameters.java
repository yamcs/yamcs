package org.yamcs.tctm.ccsds;

import java.util.Map;

import org.yamcs.YConfiguration;

/**
 * Stores configuration related to Master channels for downlink.
 * 
 * @author nm
 *
 */
public abstract class DownlinkManagedParameters {
    public enum FrameErrorDetection {NONE, CRC16, CRC32};
    
    

    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorDetection errorDetection;
    
    public DownlinkManagedParameters(YConfiguration config) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName",  null);
        errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class);
    }
    
    abstract int getMaxFrameLength();
    
    abstract int getMinFrameLength();
    abstract public Map<Integer, VcDownlinkHandler> createVcHandlers(String yamcsInstance, String linkName);
}
