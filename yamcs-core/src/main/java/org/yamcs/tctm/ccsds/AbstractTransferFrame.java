package org.yamcs.tctm.ccsds;
/**
 * Common properties for the three supported transfer frame types AOS/TM/USLP
 * 
 * @author nm
 *
 */
public abstract class AbstractTransferFrame implements TransferFrame {
    final protected int spacecraftId;
    final protected int virtualChannelId;
    final protected byte[] data;
    
    long vcFrameSeq;
    
    int dataStart;
    int dataEnd;
    int ocf;
    boolean ocfPresent = false;
    
    int firstHeaderPointer;
    
    public AbstractTransferFrame(byte[] data, int spacecraftId, int virtualChannelId) {
        this.data = data;
        this.spacecraftId = spacecraftId;
        this.virtualChannelId = virtualChannelId;
    }
    
    @Override
    public int getSpacecraftId() {
        return spacecraftId;
    }

    @Override
    public int getVirtualChannelId() {
        return virtualChannelId;
    }
    
    @Override
    public int lostFramesCount(long prevFrameSeq) {
        if(vcFrameSeq == -1 ) {
            return -1;
        }
        
        long delta = prevFrameSeq < vcFrameSeq ? vcFrameSeq - prevFrameSeq :  vcFrameSeq + getSeqCountWrapArround() - prevFrameSeq;
        delta--;
        if (delta > getSeqInterruptionDelta()) {
            return -1;
        } else {
            return (int)delta;
        }
    }
    
    public void setVcFrameSeq(long seq) {
        this.vcFrameSeq = seq;
    }
    
    @Override
    public long getVcFrameSeq() {
        return vcFrameSeq;
    }
    
    @Override
    public byte[] getData() {
        return data;
    }

    void setDataStart(int ds) {
        this.dataStart = ds;
    }
    
    
    @Override
    public int getDataStart() {
        return dataStart;
    }

    @Override
    public int getFirstHeaderPointer() {
        return firstHeaderPointer;
    }

    void setFirstHeaderPointer(int fhp) {
        this.firstHeaderPointer = fhp;
    }
    
    void setDataEnd(int offset) {
        this.dataEnd = offset;
    }

    @Override
    public int getDataEnd() {
        return dataEnd;
    }

    public void setOcf(int ocf) {
        ocfPresent = true;
        this.ocf = ocf;
    }

    @Override
    public int getOcf() {
        return ocf;
    }
    
    @Override
    public boolean hasOcf() {
        return ocfPresent;
    }
    
    abstract long getSeqCountWrapArround();
    abstract int getSeqInterruptionDelta();
}
