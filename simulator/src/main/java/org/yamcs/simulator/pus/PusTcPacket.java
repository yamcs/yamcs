package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

import org.yamcs.simulator.SimulatorCcsdsPacket;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;

/**
 * TC packets according to PUS standard
 * ECSS-E-ST-70-41C 15 April 2016
 * 
 * 
 * <pre>
 *  Secondary header (5 bytes)
 * 
 *  version number - 4 bits
 *  acknowledgement flags - 4 bits
 *  service type  -   8 bits
 *  service subtype  - 8 bits
 *  source Id - 16 bits
 * </pre>
 * 
 * 
 */
public class PusTcPacket extends SimulatorCcsdsPacket {
    public static final int SH_OFFSET = 6;
    static final int HEADER_LENGTH = SH_OFFSET + 5;
    public static final int DATA_OFFSET = HEADER_LENGTH + PusPacketConfig.DEFAULT.spareBytes();

    static final CrcCciitCalculator crcCalculator = new CrcCciitCalculator();
    private final int dataOffset;
    private final int crcLength;

    public PusTcPacket(byte[] packet) {
        this(packet, PusPacketConfig.DEFAULT.spareBytes(), PusPacketConfig.DEFAULT.tcCrc());
    }

    public PusTcPacket(byte[] packet, int spareBytes, boolean crc) {
        super(packet);
        dataOffset = HEADER_LENGTH + spareBytes;
        crcLength = crc ? 2 : 0;
    }

    public PusTcPacket(int apid, int userDataLength, int ackFlags, int type, int subtype) {
        this(apid, userDataLength, ackFlags, type, subtype, PusPacketConfig.DEFAULT.spareBytes(),
                PusPacketConfig.DEFAULT.tcCrc());
    }

    public PusTcPacket(int apid, int userDataLength, int ackFlags, int type, int subtype, int spareBytes,
            boolean crc) {
        this(new byte[HEADER_LENGTH + spareBytes + userDataLength + (crc ? 2 : 0)], spareBytes, crc);
        setHeader(apid, 1, 1, 3, getSeq(apid));

        bb.position(SH_OFFSET);
        bb.put((byte) (0x20 + (ackFlags & 0x0F)));
        bb.put((byte) type);
        bb.put((byte) subtype);
        int sourceId = 0;
        bb.putShort((short) sourceId);
    }

    public void setType(int type) {
        bb.put(SH_OFFSET + 1, (byte) type);
    }

    public int getType() {
        return bb.get(SH_OFFSET + 1) & 0xFF;
    }

    public void setSubtype(int subtype) {
        bb.put(SH_OFFSET + 2, (byte) subtype);
    }

    public int getSubtype() {
        return bb.get(SH_OFFSET + 2) & 0xFF;
    }

    public int getSourceId() {
        return bb.getShort(SH_OFFSET + 3) & 0xFFFF;
    }

    public int getAckFlags() {
        return bb.get(SH_OFFSET) & 0x0F;
    }

    @Override
    public ByteBuffer getUserDataBuffer() {
        return bb.slice(dataOffset, bb.capacity() - dataOffset - crcLength);
    }

    boolean isValid() {
        return bb.capacity() >= dataOffset + crcLength
                && (bb.get(0) & 0xF8) == 0x18
                && (bb.get(SH_OFFSET) >>> 4 & 0xF) == 2
                && (bb.getShort(4) & 0xFFFF) + 7 == bb.capacity()
                && isChecksumValid();
    }

    public boolean isChecksumValid() {
        int end = bb.capacity() - crcLength;
        return crcLength == 0 || (end >= 0
                && crcCalculator.compute(bb.array(), bb.arrayOffset(), end) == (bb.getShort(end) & 0xFFFF));
    }

    @Override
    protected void fillChecksum() {
        if (crcLength != 0) {
            writeChecksum(bb);
        }
    }

    static void writeChecksum(ByteBuffer packet) {
        int end = packet.capacity() - 2;
        int crc = crcCalculator.compute(packet.array(), packet.arrayOffset(), end);
        packet.putShort(end, (short) crc);
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("apid: ").append(getAPID())
        .append(", type: ").append(getType())
        .append(", subtype: ").append(getSubtype())
        .append("\n");
        appendBinaryData(sb);
        return sb.toString();
    }

}
