package org.yamcs.tctm.csp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
        var originalOrder = bb.order();
        bb.order(ByteOrder.BIG_ENDIAN);
        int dest = (bb.getShort(0) & 0x01F0) >>> 4;
        bb.order(originalOrder);
        return dest;
    }

    public static int getDestination(byte[] packet) {
        return (ByteArrayUtils.decodeUnsignedShort(packet, 0) & 0x01F0) >>> 4;
    }

    public int getDestinationPort() {
        var originalOrder = bb.order();
        bb.order(ByteOrder.BIG_ENDIAN);
        int dport = (bb.getShort(1) & 0x0FC0) >>> 6;
        bb.order(originalOrder);
        return dport;
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

    public void setHeader(byte priority, byte source, byte destination, byte destinationPort, byte sourcePort) {
        setHeader(priority, source, destination, destinationPort, sourcePort, false, false, false, false);
    }

    public void setHeader(byte priority, byte source, byte destination, byte destinationPort, byte sourcePort,
            boolean hmacFlag, boolean xteaFlag, boolean rdpFlag, boolean crcFlag) {
        int headerBits = (priority & 0b11) << 30 |
                (source & 0b11111) << 25 |
                (destination & 0b11111) << 20 |
                (destinationPort & 0b111111) << 14 |
                (sourcePort & 0b111111) << 8 |
                (byte) (hmacFlag ? (0b1 << 3) : 0b0) |
                (byte) (xteaFlag ? (0b1 << 2) : 0b0) |
                (byte) (rdpFlag ? (0b1 << 1) : 0b0) |
                (byte) (crcFlag ? 0b1 : 0b0);
        bb.putInt(0, headerBits);
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
