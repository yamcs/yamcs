package org.yamcs.tctm.ccsds;

/**
 * 
 * @author nm
 * TM Transfer Frame as per 
 * 
 * CCSDS RECOMMENDED STANDARD FOR TM SPACE DATA LINK PROTOCOL 
 * CCSDS 132.0-B-2 September 2015 
 *
 */
public class TmTransferFrame extends DownlinkTransferFrame {
    private int shStart = -1;
    private int shLength =-1;
    private boolean idle;
    
    public TmTransferFrame(byte[] data, int spacecraftId, int virtualChannelId) {
        super(data, spacecraftId, virtualChannelId);
    }

    @Override
    public boolean containsOnlyIdleData() {
        return idle;
    }

    @Override
    long getSeqCountWrapArround() {
        return 0xFF;
    }

    @Override
    int getSeqInterruptionDelta() {
        return 100;
    }
    
    /**
     * 
     * @return the start offset of the secondary header or -1 if the frame does not have a secondary header
     */
    public int getShStart() {
        return shStart;
    }
    
    /**
     * Set secondary header start offset. Set to -1 if the frame does not contain a secondary header
     * @param offset
     */
    public void setShStart(int offset) {
        this.shStart = offset;
    }

    /**
     * Return the length of the secondary header in bytes
     * @return
     */
    public int getShLength() {
        return shLength;
    }
    /**
     * Set secondary header length in bytes
     * @param length
     */
    public void setShLength(int length) {
        this.shLength = length;
    }

    /**
     * Set frame as idle
     * @param idle - if the frame is idle or not
     */
    void setIdle(boolean idle) {
        this.idle = idle;
    }

    @Override
    public String toString() {
        return "TmTransferFrame [shStart=" + shStart + ", shLength=" + shLength + ", idle=" + idle
                + ", masterChannelId=" + spacecraftId + ", virtualChannelId=" + virtualChannelId + ", vcFrameSeq="
                + vcFrameSeq + ", dataStart=" + dataStart + ", dataEnd=" + dataEnd + ", ocf=" + Integer.toHexString(ocf) + ", fps=" + firstHeaderPointer
                + "]";
    }
    
    
}
