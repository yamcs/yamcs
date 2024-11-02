package org.yamcs.tctm;

import java.nio.ByteBuffer;

import org.yamcs.utils.ByteArrayUtils;

/**
 * CCSDS Packet as per CCSDS 133.0-B-2 https://public.ccsds.org/Pubs/133x0b2e1.pdf
 *
 * <pre>
 * primary header (6 bytes):
 *  3 bit = version - 0
 *  1 bit = type (0 = TM, 1= TC)
 *  1 bit = 2nd header present
 *  11 bit = apid
 *
 *  2 bit = grouping, 01 = first, 00 = cont, 10 = last packet of group, 11 = unsegmented data
 *  14 bit = seq
 *
 *  16 bit = packet length (excluding primary header) minus 1
 * </pre>
 *
 */
public class CcsdsPacket {
    protected ByteBuffer bb;

    public CcsdsPacket(byte[] packet) {
        bb = ByteBuffer.wrap(packet);
    }

    public CcsdsPacket(ByteBuffer bb) {
        this.bb = bb;
    }

    public static CcsdsPacket wrap(byte[] pkt) {
        return new CcsdsPacket(pkt);
    }

    public int getSecondaryHeaderFlag() {
        return (bb.getShort(0) >> 11) & 1;
    }

    static public boolean getSecondaryHeaderFlag(byte[] packet) {
        return (packet[0] & 0x8) == 0x8;
    }

    public int getSequenceCount() {
        return bb.getShort(2) & 0x3FFF;
    }

    public static int getSequenceCount(byte[] packet) {
        return ByteArrayUtils.decodeUnsignedShort(packet, 2) & 0x3FFF;
    }

    public void setSequenceCount(short seqCount) {
        short oldSeqField = bb.getShort(2);
        short seqInd = (short) (oldSeqField & (~0x3FFF));
        bb.putShort(2, (short) ((seqCount & 0x3FFF) + seqInd));
    }

    public static short getSequenceCount(ByteBuffer bb) {
        return (short) (bb.getShort(2) & 0x3FFF);
    }

    public int getAPID() {
        return bb.getShort(0) & 0x07FF;
    }

    /**
     * Write the header. The grouping is set to 10b = last packet in the group and the length to the capacity of the
     * buffer (minus 7)
     *
     * @param apid
     * @param secHeaderPresent
     *            1 = present, 0 = absent
     * @param tmtc
     *            0 = tm, 1 = tc
     * @param seqFlags
     *            grouping: 01 = first packet, 00 = continuation packet, 10 = last packet of group, 11 = unsegmented
     *            data
     * @param seq
     */
    public void setHeader(int apid, int tmtc, int secHeaderPresent, int seqFlags, int seq) {
        secHeaderPresent &= 1;
        tmtc &= 1;
        seq &= 0x3FFF;
        seqFlags &= 3;

        short w = (short) ((tmtc << 12) | (secHeaderPresent << 11) | apid);
        bb.putShort(0, w);
        w = (short) ((seqFlags << 14) | seq);
        bb.putShort(2, w);
        bb.putShort(4, (short) (bb.capacity() - 7));
    }

    public void setAPID(int apid) {
        int tmp = bb.getShort(0) & (~0x07FF);
        tmp = tmp | apid;
        bb.putShort(0, (short) tmp);
    }

    public static short getAPID(ByteBuffer bb) {
        return (short) (bb.getShort(0) & 0x07FF);
    }


    /* returns the length written in the ccsds header */
    public static int getCcsdsPacketLength(ByteBuffer bb) {
        return bb.getShort(4) & 0xFFFF;
    }


    /* returns the length written in the ccsds header */
    public int getCcsdsPacketLength() {
        return getCcsdsPacketLength(bb);
    }


    public void setCcsdsPacketLength(short length) {
        // return bb.getShort(4)&0xFFFF;
        bb.putShort(4, length);
    }

    /** returns the length of the packet, normally equals ccsdslength+7 */
    public int getLength() {
        return bb.capacity();
    }

    /**
     * @return time in seconds since 6 Jan 1980
     */
    public long getCoarseTime() {
        return bb.getInt(6) & 0xFFFFFFFFL;
    }

    public int getTimeId() {
        return (bb.get(11) & 0xFF) >> 6;
    }

    public boolean getChecksumIndicator() {
        return (bb.get(11) & 0x20) == 0x20;
    }

    static public boolean getChecksumIndicator(byte[] packet) {
        return (packet[11] & 0x20) == 0x20;
    }

    public byte[] getBytes() {
        if (bb.hasArray() && bb.array().length == bb.capacity() && !bb.isReadOnly()) {
            return bb.array();
        }
        byte[] b = new byte[bb.capacity()];
        int pos = bb.position();
        bb.get(b);
        bb.position(0);
        bb.position(pos);

        return b;
    }

    public ByteBuffer getByteBuffer() {
        return bb;
    }

    public static short getAPID(byte[] packet) {
        return (short) (ByteArrayUtils.decodeUnsignedShort(packet, 0) & 0x07FF);
    }

    public static int getCcsdsPacketLength(byte[] buf) {
        return getCcsdsPacketLength(ByteBuffer.wrap(buf));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("apid: ").append(getAPID()).append("\n");
        appendBinaryData(sb);
        return sb.toString();
    }

    protected void appendBinaryData(StringBuilder sb) {
        StringBuilder text = new StringBuilder();
        int len = bb.limit();
        int lengthRoundedUpToNextMultipleOf16 = (int) Math.ceil(len / 16.0) * 16;
        byte c;
        for (int i = 0; i < lengthRoundedUpToNextMultipleOf16; ++i) {
            // If we are at the beginning of a 16 byte multiple
            if (i % 16 == 0) {
                sb.append(String.format("%04x:", i));
                text.setLength(0);
            }

            // For every 2 bytes, insert an extra space
            if ((i & 1) == 0) {
                sb.append(" ");
            }

            // If we did not reach the end of the buffer
            if (i < len) {
                c = bb.get(i);
                // Add 2 byte hexadecimal translation of the byte
                sb.append(String.format("%02x", 0xFF & c));
                // Add printable characters or a dot to the ASCII buffer of the line being parsed
                text.append(((c >= ' ') && (c <= 127)) ? String.format("%c", c) : ".");
            } else { // If we reached the end of the buffer
                // Pad with spaces
                sb.append("  ");
                text.append(" ");
            }

            // If we reached the end of a 16 byte multiple
            if ((i + 1) % 16 == 0) {
                // Append the ASCII buffer of the parsed line
                sb.append(" ");
                sb.append(text);
                sb.append("\n");
            }
        }
    }



}
