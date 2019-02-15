package org.yamcs.tctm.ccsds;

import java.util.Map;

/**
 * Stores configuration related to Master channels
 * @author nm
 *
 */
public abstract class ManagedParameters {
    enum FrameErrorCorrection {NONE, CRC16, CRC32};
    
    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorCorrection errorCorrection;
    
    abstract int getMaxFrameLength();
    
    abstract int getMinFrameLength();

    abstract Map<Integer, VirtualChannelHandler> createVcHandlers(String yamcsInstance, String parentLinkName);

}
