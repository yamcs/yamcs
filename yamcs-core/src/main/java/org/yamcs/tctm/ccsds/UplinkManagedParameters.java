package org.yamcs.tctm.ccsds;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.YConfiguration;

/**
 * Stores configuration related to Master channels for uplink.
 * 
 * @author nm
 *
 */
public abstract class UplinkManagedParameters {
    public enum FrameErrorDetection {NONE, CRC16, CRC32};
   
    public enum ServiceType {
        PACKET
    };
    
    
    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorDetection errorDetection;
    
    public UplinkManagedParameters(YConfiguration config) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName",  null);
        this.errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class, FrameErrorDetection.CRC16);
    }
    
    abstract int getMaxFrameLength();
    
    abstract public  List<VcUplinkHandler> createVcHandlers(String yamcsInstance, String linkName, ScheduledThreadPoolExecutor executor);
}
