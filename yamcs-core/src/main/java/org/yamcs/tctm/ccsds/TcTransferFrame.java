package org.yamcs.tctm.ccsds;

import java.util.List;

import org.yamcs.commanding.PreparedCommand;

/**
 * 
 * TC Transfer Frame as per
 * <p>
 * CCSDS RECOMMENDED STANDARD FOR TC SPACE DATA LINK PROTOCOL
 * CCSDS 232.0-B-4 October 2021
 *
 * <p>
 * Frame structure:
 * <ul>
 * <li>Transfer Frame Primary Header (5 bytes mandatory)</li>
 * <li>Transfer Frame Data Field (up to 1019 or 1017 bytes, mandatory)</li>
 * <li>Frame Error Control Field (2 bytes, optional).</li>
 * </ul>
 * The Transfer Frame Primary Header structure:
 * <ul>
 * <li>Transfer Frame Version Number (2 bits, mandatory)</li>
 * <li>Bypass Flag (1 bit, mandatory)</li>
 * <li>Control Command Flag (1 bit, mandatory)</li>
 * <li>Reserved Spare (2 bits, mandatory)</li>
 * <li>Spacecraft Identifier (10 bits, mandatory)</li>
 * <li>Virtual Channel Identifier (6 bits, mandatory)</li>
 * <li>Frame Length (10 bits, mandatory)</li>
 * <li>Frame Sequence Number (8 bits, mandatory)</li>
 * </ul>
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

    @Override
    public String toString() {
        return "TcTransferFrame [masterChannelId=" + spacecraftId + ", virtualChannelId=" + virtualChannelId
                + ", vcFrameSeq=" + vcFrameSeq + ", bypass=" + bypass + ", cmdControl=" + cmdControl
                + ", numCommands: " + ((commands != null) ? commands.size() : 0) + "]";
    }

}
