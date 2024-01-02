package org.yamcs.tctm.csp;

import java.nio.ByteBuffer;

import org.yamcs.utils.ByteArrayUtils;

/**
 * Helper class for accessing CubeSat Space Protocol v1 fields
 */
public class CspPacket {

    protected ByteBuffer bb;

    public CspPacket(byte[] packet) {
        bb = ByteBuffer.wrap(packet);
    }

    public CspPacket(ByteBuffer bb) {
        this.bb = bb;
    }

    public int getPriority() {
        return (bb.get(0) & 0xC0) >>> 6;
    }

    public int getSource() {
        return (bb.get(0) & 0x3E) >>> 1;
    }

    public static int getSource(byte[] packet) {
        return (packet[0] & 0x3E) >>> 1;
    }

    public int getSourcePort() {
        return bb.get(2) & 0x3F;
    }

    public int getDestination() {
        return (bb.getShort(0) & 0x01F0) >>> 4;
    }

    public static int getDestination(byte[] packet) {
        return (ByteArrayUtils.decodeUnsignedShort(packet, 0) & 0x01F0) >>> 4;
    }

    public int getDestinationPort() {
        return (bb.getShort(1) & 0x0FC0) >>> 6;
    }

    public boolean getHmacFlag() {
        return (bb.get(3) & 0x08) == 0x08;
    }

    public boolean getXteaFlag() {
        return (bb.get(3) & 0x04) == 0x04;
    }

    public boolean getRdpFlag() {
        return (bb.get(3) & 0x02) == 0x02;
    }

    public boolean getCrcFlag() {
        return (bb.get(3) & 0x01) == 0x01;
    }

    public static boolean getCrcFlag(byte[] packet) {
        return (packet[3] & 0x01) == 0x01;
    }

    public static void setCrcFlag(byte[] packet, boolean enabled) {
        packet[3] |= 0x01;
    }

    public int getLength() {
        return bb.capacity();
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

    @Override
    public String toString() {
        var buf = new StringBuilder("S ")
                .append(getSource())
                .append(":")
                .append(getSourcePort())
                .append(", D ")
                .append(getDestination())
                .append(":")
                .append(getDestinationPort());
        return buf.toString();
    }
}
