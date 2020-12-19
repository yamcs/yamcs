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
 * @author nm
 *
 */
public class PusTcPacket extends SimulatorCcsdsPacket {
    public static final int SH_OFFSET = 6;
    public static final int DATA_OFFSET = SH_OFFSET + 5;

    static final CrcCciitCalculator crcCalculator = new CrcCciitCalculator();

    public PusTcPacket(byte[] packet) {
        super(packet);
    }

    public PusTcPacket(int apid, int userDataLength, int ackFlags, int type, int subtype) {
        super(ByteBuffer.allocate(getPacketLength(userDataLength)));
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

    private static int getPacketLength(int userDataLength) {
        return DATA_OFFSET + userDataLength + 2;// 2 bytes for the CRC
    }

    public int getAckFlags() {
        return bb.get(DATA_OFFSET);
    }

    @Override
    public ByteBuffer getUserDataBuffer() {
        bb.position(DATA_OFFSET);
        return bb.slice();
    }

    @Override
    protected void fillChecksum() {
        int crc = crcCalculator.compute(bb.array(), bb.arrayOffset(), bb.capacity() - bb.arrayOffset() - 2);
        bb.position(bb.capacity() - 2);
        bb.putShort((short) crc);
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
