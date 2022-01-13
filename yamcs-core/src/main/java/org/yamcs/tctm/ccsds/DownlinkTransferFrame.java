package org.yamcs.tctm.ccsds;

import org.yamcs.time.Instant;

/**
 * Transfer frame is an interface covering the three CCSDS transfer frames types:
 * <ul>
 * <li>TM (CCSDS 132.0-B-2)</li>
 * <li>AOS (CCSDS 732.0-B-3)</li>
 * <li>UNIFIED ( 732.1-B-1)</li>
 * </ul>
 * <p>
 * All three of them have the following structure:
 * <ul>
 * <li>Primary Header</li>
 * <li>Insert Zone/Secondary Header</li>
 * <li>Data Field</li>
 * <li>Operational Control Field (OCF)</li>
 * <li>Error Control Field</li>
 * </ul>
 * <p>
 * Note that for USLP, the data field has also a header.
 * <p>
 * In the dataStart, and dataLength properties below, only the real data (i.e. excluding the data field header) is
 * considered.
 * <p>
 * 
 * The idea is that the {@link VcDownlinkHandler} that deals with the data, has to have all the information on how
 * to interpret the data part.
 * <p>
 * For the purpose of packet extraction each frame has defined three offsets:<br>
 * dataStart &lt;= firstSduStart &lt; dataEnd
 * <p>
 * firstSduStart refers to the first SDU that starts in this frame.
 * <p>
 * The data in between dataStart and firstSduStart is part of a previous packet and will be used only if there is no
 * discontinuity in the frame sequence count.
 * 
 * @author nm
 *
 */
public abstract class DownlinkTransferFrame {
    final protected int spacecraftId;
    final protected int virtualChannelId;
    final protected byte[] data;
    
    long vcFrameSeq;
    
    int dataStart;
    int dataEnd;
    int ocf;
    boolean ocfPresent = false;
    
    int firstHeaderPointer;
    private Instant ertime = Instant.INVALID_INSTANT;
    
    public DownlinkTransferFrame(byte[] data, int spacecraftId, int virtualChannelId) {
        this.data = data;
        this.spacecraftId = spacecraftId;
        this.virtualChannelId = virtualChannelId;
    }
    /**
     * 
     * @return master channel id
     */
    public int getSpacecraftId() {
        return spacecraftId;
    }
    /**
     * 
     * @return virtual channel id
     */
    public int getVirtualChannelId() {
        return virtualChannelId;
    }
    
    /**
     * Returns the number of frames lost from the previous sequence to this one.
     * If no frame has been lost (i.e. if prevFrameSeq and getFrameSeq() are in order) then return 0.
     * 
     * -1 means that a number of lost frames could not be determined - if there is some indication that the stream has
     * been reset
     * 
     * @param prevFrameSeq
     * @return
     */
    public int lostFramesCount(long prevFrameSeq) {
        if(vcFrameSeq == -1 ) {
            return -1;
        }

        long delta = prevFrameSeq < vcFrameSeq
                   ? vcFrameSeq - prevFrameSeq
                   : vcFrameSeq + getSeqCountWrapArround() - prevFrameSeq + 1;
        delta--;
        if (delta > getSeqInterruptionDelta()) {
            return -1;
        } else {
            return (int)delta;
        }
    }
    /**
     * Set the virtual channel frame count
     * 
     * @param seq
     */
    public void setVcFrameSeq(long seq) {
        this.vcFrameSeq = seq;
    }
    /**
     * 
     * @return virtual channel frame count
     */
    public long getVcFrameSeq() {
        return vcFrameSeq;
    }
    
    
    public byte[] getData() {
        return data;
    }

    void setDataStart(int ds) {
        this.dataStart = ds;
    }
    
    /**
     * Where in the byte array returned by {@link #getData} starts the data.
     * 
     * @return
     */
    public int getDataStart() {
        return dataStart;
    }


    /**
     * Where in the byte array returned by {@link #getData} starts the first packet (assuming this is a frame containing
     * packets).
     * 
     * Returns -1 if there is no packet starting in this frame.
     * 
     * @return the offset of the first packet that starts in this frame or -1 if no packet starts in this frame
     */
    public int getFirstHeaderPointer() {
        return firstHeaderPointer;
    }

    void setFirstHeaderPointer(int fhp) {
        this.firstHeaderPointer = fhp;
    }
    
    void setDataEnd(int offset) {
        this.dataEnd = offset;
    }

    /**
     * The offset in the buffer where the data ends.
     * 
     * @return data end
     */
    public int getDataEnd() {
        return dataEnd;
    }

    public void setOcf(int ocf) {
        ocfPresent = true;
        this.ocf = ocf;
    }
    /**
     * Get the 4 bytes operational control field. This has a meaningful value only if the {@link #hasOcf} returns
     * true.
     * 
     * @return the Operational Control Field.
     */
    public int getOcf() {
        return ocf;
    }
    /**
     * 
     * @return true if this frame has an Operation Control Field set.
     */
    public boolean hasOcf() {
        return ocfPresent;
    }
    /**
     * 
     * @return the earth reception time of the frame
     */
    public Instant getEarthRceptionTime() {
        return ertime;
    }

    public void setEearthRceptionTime(Instant ertime) {
        this.ertime = ertime;
    }
    
    abstract long getSeqCountWrapArround();
    abstract int getSeqInterruptionDelta();
    
    /**
     * 
     * @return true if this frame contains only idle (fill) data.
     */
    abstract boolean containsOnlyIdleData();
}
