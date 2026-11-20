package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.simulator.SimulatorCcsdsPacket;

/**
 * TM packets according to PUS standard
 * ECSS-E-ST-70-41C 15 April 2016
 * 
 * 
 * <pre>
 * Secondary header
 * 
 *  version number - 4 bits
 *  spacecraft time reference status - 4 bits
 *  service type  -   8 bits
 *  message subtype  - 8 bits
 *  message type counter - 16 bits
 *  destination Id - 16 bits
 *  time - configurable CUC width
 *  spare field - four bytes in the current profile
 * </pre>
 * 
 * 
 * @author nm
 *
 */
public class PusTmPacket extends SimulatorCcsdsPacket {
    public static final int SH_OFFSET = 6;
    private static final int TIME_OFFSET = SH_OFFSET + 7;
    public static final int DATA_OFFSET = TIME_OFFSET + PusTimeEncoding.DEFAULT.getEncodedLength()
            + PusPacketConfig.DEFAULT.spareBytes();

    private static final ConcurrentHashMap<Integer, AtomicInteger> countMap = new ConcurrentHashMap<>();
    private final int dataOffset;
    private final int spareBytes;
    private final int crcLength;

    public PusTmPacket(byte[] packet) {
        this(packet, PusTimeEncoding.DEFAULT, PusPacketConfig.DEFAULT.spareBytes(), PusPacketConfig.DEFAULT.tmCrc());
    }

    public PusTmPacket(byte[] packet, PusTimeEncoding timeEncoding, int spareBytes, boolean crc) {
        super(packet);
        this.spareBytes = spareBytes;
        this.crcLength = crc ? 2 : 0;
        dataOffset = TIME_OFFSET + timeEncoding.getEncodedLength() + spareBytes;
    }

    public PusTmPacket(int apid, int userDataLength, int type, int subtype) {
        this(apid, userDataLength, type, subtype, PusTimeEncoding.DEFAULT);
    }

    public PusTmPacket(int apid, int userDataLength, int type, int subtype, PusTimeEncoding timeEncoding) {
        this(apid, userDataLength, type, subtype, timeEncoding, PusPacketConfig.DEFAULT.spareBytes(),
                PusPacketConfig.DEFAULT.tmCrc());
    }

    public PusTmPacket(int apid, int userDataLength, int type, int subtype, PusTimeEncoding timeEncoding,
            int spareBytes, boolean crc) {
        this(new byte[TIME_OFFSET + timeEncoding.getEncodedLength() + spareBytes + userDataLength + (crc ? 2 : 0)],
                timeEncoding, spareBytes, crc);
        setHeader(apid, 0, 1, 3, getSeq(apid));
        bb.position(SH_OFFSET);
        bb.put((byte) (0x21));
        bb.put((byte) type);
        bb.put((byte) subtype);
        int destination = 0;
        bb.putShort((short) getCount(destination));
        bb.putShort((short) destination);
        PusTime now = timeEncoding.now();
        now.encode(bb, timeEncoding);
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

    void setPage(int pageNumber, int totalPages) {
        if (pageNumber < 1 || totalPages < pageNumber || totalPages > 0xFFFF) {
            throw new IllegalArgumentException("Invalid report page " + pageNumber + " of " + totalPages);
        }
        if (spareBytes == 4) {
            bb.putShort(dataOffset - 4, (short) pageNumber);
            bb.putShort(dataOffset - 2, (short) totalPages);
        }
    }

    public int getUserDataOffset() {
        return dataOffset;
    }

    @Override
    public ByteBuffer getUserDataBuffer() {
        return bb.slice(dataOffset, bb.capacity() - dataOffset - crcLength);
    }

    @Override
    protected void fillChecksum() {
        if (crcLength != 0) {
            PusTcPacket.writeChecksum(bb);
        }
    }

    protected static int getCount(int apid) {
        AtomicInteger count = countMap.computeIfAbsent(apid, a -> new AtomicInteger(0));
        return count.getAndIncrement() & 0xFFFF;
    }
}
