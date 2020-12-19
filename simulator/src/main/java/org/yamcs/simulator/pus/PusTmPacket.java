package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.simulator.SimulatorCcsdsPacket;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;

/**
 * TM packets according to PUS standard
 * ECSS-E-ST-70-41C 15 April 2016
 * 
 * 
 * <pre>
 * Secondary header (16 bytes)
 * 
 *  version number - 4 bits
 *  spacecraft time reference status - 4 bits
 *  service type  -   8 bits
 *  message subtype  - 8 bits
 *  message type counter - 16 bits
 *  destination Id - 16 bits
 *  time - variable size but we use 9 bytes
 * </pre>
 * 
 * 
 * @author nm
 *
 */
public class PusTmPacket extends SimulatorCcsdsPacket {
    public static final int SH_OFFSET = 6;
    public static final int DATA_OFFSET = SH_OFFSET + 7 + PusTime.LENGTH_BYTES;

    static final CrcCciitCalculator crcCalculator = new CrcCciitCalculator();

    protected static HashMap<Integer, AtomicInteger> countMap = new HashMap<>(2); // destination -> msgCounter

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
        bb.putShort((short) getCount(destination));
        bb.putShort((short) destination);
        PusTime now = PusTime.now();
        now.encode(bb);
    }

    public void setType(int type) {
        bb.put(SH_OFFSET + 1, (byte) type);

    }

    public void setSubtype(int subtype) {
        bb.put(SH_OFFSET + 2, (byte) subtype);
    }

    private static int getPacketLength(int userDataLength) {
        return DATA_OFFSET + userDataLength + 2;// 2 bytes for the CRC
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

    protected static int getCount(int apid) {
        AtomicInteger count = countMap.computeIfAbsent(apid, a -> new AtomicInteger(0));
        return count.getAndIncrement() & 0xFFFF;
    }
}
