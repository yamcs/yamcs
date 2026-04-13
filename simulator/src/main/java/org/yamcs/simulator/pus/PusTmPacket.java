package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import org.yamcs.simulator.SimulatorCcsdsPacket;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;

public class PusTmPacket extends SimulatorCcsdsPacket {
    public static final int SH_OFFSET = 6;
    // PUS secondary header (5 bytes) + TM CUC time (6 bytes) = 11 bytes after CCSDS primary header.
    public static final int DATA_OFFSET = SH_OFFSET + 5 + PusTime.TM_CUC_LENGTH_BYTES;

    static final CrcCciitCalculator crcCalculator = new CrcCciitCalculator();

    public PusTmPacket(byte[] packet) {
        super(packet);
    }

    public PusTmPacket(int apid, int userDataLength, int type, int subtype) {
        super(ByteBuffer.allocate(getPacketLength(userDataLength)));
        setHeader(apid, 0, 1, 3, getSeq(apid));
        bb.position(SH_OFFSET);
        bb.put((byte) (0x21));
        bb.put((byte) type);
        bb.put((byte) subtype);
        int destination = 0;
        bb.putShort((short) destination);
        PusTime now = PusTime.now();
        now.encodeTmCuc(bb);
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
        return DATA_OFFSET + userDataLength + 2;
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

}
