package org.yamcs.tctm.ccsds;

import java.util.List;

import org.yamcs.commanding.PreparedCommand;

public abstract class UplinkTransferFrame {
    final protected boolean cmdControl;
    final protected int spacecraftId;
    final protected int virtualChannelId;
    protected boolean bypass;

    int vcFrameSeq;
    long genTime;

    List<PreparedCommand> commands;
    byte[] data;
    int dataStart;
    int dataEnd;

    public UplinkTransferFrame(byte[] data, int spacecraftId, int virtualChannelId, boolean cmdControl) {
        this.spacecraftId = spacecraftId;
        this.virtualChannelId = virtualChannelId;
        this.data = data;
        this.cmdControl = cmdControl;
    }

    public boolean isCmdControl() {
        return cmdControl;
    }

    public boolean isBypass() {
        return bypass;
    }

    public void setBypass(boolean bypass) {
        this.bypass = bypass;
    }

    public int bypassFlag() {
        return bypass ? 1 : 0;
    }

    public int cmdControlFlag() {
        return cmdControl ? 1 : 0;
    }

    /**
     * 
     * returns the data of the frame - that is frame header + frame data + optional checksum
     * <p>
     * The frame data starts and ends at the offsets returned by {@link #getDataStart()} and {@link #getDataEnd()}
     * respectively
     */
    public byte[] getData() {
        return data;
    }

    /**
     * 
     * returns the offset of the data start inside the frame
     * <p>
     * For CCSDS TC frames the offset is always 5
     */
    public int getDataStart() {
        return dataStart;
    }

    /**
     * 
     * returns the offset of the data end inside the frame
     * <p>
     * For CCSDS TC frames this is the end of the frame or 2 bytes before depending whether error control is used or
     * not.
     */
    public int getDataEnd() {
        return dataEnd;
    }

    public void setVcFrameSeq(int vS) {
        vcFrameSeq = vS;
    }

    public int getVcFrameSeq() {
        return vcFrameSeq;
    }

    public void setDataStart(int start) {
        this.dataStart = start;
    }

    public void setDataEnd(int end) {
        this.dataEnd = end;
    }

    public int getVirtualChannelId() {
        return virtualChannelId;
    }

    /**
     * 
     * @return the list of commands that compose this frame. It could be null (e.g. for BC frames)
     */
    public List<PreparedCommand> getCommands() {
        return commands;
    }

    public void setCommands(List<PreparedCommand> commands) {
        this.commands = commands;
    }

    public long getGenerationTime() {
        return genTime;
    }
}
