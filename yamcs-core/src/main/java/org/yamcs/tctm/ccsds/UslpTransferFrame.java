package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.ccsds.UslpManagedParameters.ServiceType;

/**
 * Transfer Frames as per:
 * 
 * CCSDS RECOMMENDED STANDARD FOR UNIFIED SPACE DATA LINK PROTOCOL
 * CCSDS 732.1-B-1 October 2018
 * 
 * 
 * @author nm
 * 
 */
public class UslpTransferFrame extends DownlinkTransferFrame {
    private int mapId;
    private long seqCountWrapArround;
    ServiceType serviceType;

    public UslpTransferFrame(byte[] data, int masterChannelId, int virtualChannelId) {
        super(data, masterChannelId, virtualChannelId);
    }

    @Override
    protected int getSeqInterruptionDelta() {
        return (int) (seqCountWrapArround >> 2);
    }

    @Override
    public boolean containsOnlyIdleData() {
        return serviceType == ServiceType.IDLE;
    }

    /**
     * Sets the max value for the frame count for this frame.
     * 
     * @param n
     */
    public void setSeqCountWrapArround(long n) {
        this.seqCountWrapArround = n;
    }

    @Override
    long getSeqCountWrapArround() {
        return seqCountWrapArround;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public int getMapId() {
        return mapId;
    }

    public void setServiceType(ServiceType type) {
        this.serviceType = type;
    }
}
