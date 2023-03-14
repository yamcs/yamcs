package org.yamcs.simulator;

import java.nio.ByteBuffer;
import org.yamcs.utils.TimeEncoding;

/**
 * CCSDS packets as used in Columbus/ISS
 * 
 * <pre>
 * primary header (6 bytes):
 * 3 bit = version
 * 1 bit = type (0 = system packet, 1 = payload packet)
 * 1 bit = 2nd header present
 * 11 bit = apid
 * 
 * 2 bit = grouping, 01 = first, 00 = cont, 10 = last packet of group
 * 14 bit = seq
 * 
 * 16 bit = packet length (excluding primary header) minus 1
 * 
 * secondary header (10 bytes):
 * 32 bit = coarse time (seconds since 1970)
 * 8 bit = fine time
 * 2 bits = time id (see constants)
 * 1 bit = checksum present (2 bytes after user data)
 * 5 bits = packet type (see constants)
 * 32 bit = packet id
 * </pre>
 */
public class ColumbusCcsdsPacket extends SimulatorCcsdsPacket {
    final byte SH_TIME_ID_NO_TIME_FIELD = 0;
    final byte SH_TIME_ID_TIME_OF_PACKET_GENERATION = 1;
    final byte SH_TIME_ID_TIME_TAG = 2;
    final byte SH_TIME_ID_UNDEFINED = 3;

    // Packet types
    final static byte SH_PKT_TYPE_CCSDS_CCSDS_PAYLOAD_HK_PACKET = 5;
    final static byte SH_PKT_TYPE_CCSDS_PAYLOAD_COMMAND_PACKET = 10;
    final static byte SH_PKT_TYPE_CCSDS_MEMORY_LOAD_PACKET = 11;
    final static byte SH_PKT_TYPE_CCSDS_RESPONSE_PACKET = 12;

    private int packetid, packetType;
    private long timeMillis; // yamcs time

    private boolean checksumPresent;

    public ColumbusCcsdsPacket(byte[] packet) {
        super(packet);
        readHeader();
    }

    public ColumbusCcsdsPacket(ByteBuffer bb) {
        super(bb);
        readHeader();
    }

    public ColumbusCcsdsPacket(int apid, int userDataLength, int packetid) {
        this(apid, userDataLength, packetid, true);
    }

    public ColumbusCcsdsPacket(int apid, int userDataLength, int packetid, boolean checksumPresent) {
        this(apid, userDataLength, SH_PKT_TYPE_CCSDS_CCSDS_PAYLOAD_HK_PACKET, packetid, checksumPresent);
    }

    public ColumbusCcsdsPacket(int apid, int userDataLength, int packetType, int packetid, boolean checksumPresent) {
        super(ByteBuffer.allocate(getPacketLength(userDataLength, checksumPresent)));
        setHeader(apid, 0, 1, 3, getSeq(apid));

        timeMillis = TimeEncoding.getWallclockTime(); // gps time as of 2017

        this.packetType = packetType;
        this.packetid = packetid;
        this.checksumPresent = checksumPresent;

        putHeader();
    }

    private static int getPacketLength(int userDataLength, boolean checksumPresent) {
        int pl = userDataLength + 16;
        if (checksumPresent) {
            pl += 2;
            if ((pl & 1) == 1) { // need an even number of bytes to compute a checksum
                pl += 1;
            }
        }
        return pl;
    }

    public ByteBuffer getUserDataBuffer() {
        bb.position(16);
        return bb.slice();
    }

    public int getPacketId() {
        return packetid;
    }

    public void setPacketId(int packetId) {
        this.packetid = packetId;
    }

    public int getPacketType() {
        return packetType;
    }

    public void setTime(long instant) {
        timeMillis = instant;
        putHeader();
    }

    private void putHeader() {
        long gpsMillis = TimeEncoding.toGpsTimeMillisec(timeMillis);
        bb.putInt(6, (int) (gpsMillis / 1000));
        bb.put(10, (byte) ((gpsMillis % 1000) * 256 / 1000));
        int checksum = checksumPresent ? 1 : 0;
        bb.put(11, (byte) ((SH_TIME_ID_TIME_OF_PACKET_GENERATION << 6) | (checksum << 5) | packetType));
        bb.putInt(12, packetid);
        // describePacketHeader();
    }

    private void readHeader() {
        this.timeMillis = TimeEncoding.fromGpsCcsdsTime(bb.getInt(6), bb.get(10));
        this.packetType = bb.get(11) & 0x1F;
        this.packetid = bb.getInt(12);
        this.checksumPresent = this.getChecksumIndicator();
    }

    @Override
    public void fillChecksum() {
        if (checksumPresent) {
            int checksum = 0;
            for (int i = 0; i < bb.capacity() - 2; i += 2) {
                checksum += bb.getShort(i);
            }
            bb.putShort(bb.capacity() - 2, (short) checksum);
        }
    }
}
