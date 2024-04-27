package org.yamcs.tctm.ccsds;

import java.util.List;

import org.yamcs.commanding.PreparedCommand;

/**
 * 
 * @author nm
 *         TC Transfer Frame as per
 * 
 *         CCSDS RECOMMENDED STANDARD FOR TC SPACE DATA LINK PROTOCOL
 *         CCSDS 232.0-B-3 September 2015
 *
 */
public class TcTransferFrame {
    private boolean cmdControl;
    private boolean bypass;
    final protected int spacecraftId;
    final protected int virtualChannelId;
    int vcFrameSeq;
    long genTime;

    List<PreparedCommand> commands;
    byte[] data;
    int dataStart;
    int dataEnd;

    public TcTransferFrame(byte[] data, int spacecraftId, int virtualChannelId) {
        this.spacecraftId = spacecraftId;
        this.virtualChannelId = virtualChannelId;
        this.data = data;
    }

    public boolean isCmdControl() {
        return cmdControl;
    }

    public void setCmdControl(boolean cmdControl) {
        this.cmdControl = cmdControl;
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

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public int getDataStart() {
        return dataStart;
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

    public int getDataEnd() {
        return dataEnd;
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

    @Override
    public String toString() {
        return "TcTransferFrame [masterChannelId=" + spacecraftId + ", virtualChannelId=" + virtualChannelId
                + ", vcFrameSeq=" + vcFrameSeq + ", bypass=" + bypass + ", cmdControl=" + cmdControl
                + ", numCommands: " + ((commands != null) ? commands.size() : 0) + "]";
    }

}
