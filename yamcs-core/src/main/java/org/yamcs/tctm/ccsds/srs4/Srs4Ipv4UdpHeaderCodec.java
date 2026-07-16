package org.yamcs.tctm.ccsds.srs4;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.tctm.TcTmException;
import org.yamcs.utils.ByteArrayUtils;

import org.yamcs.tctm.ccsds.srs4.Srs4Config.Ipv4Endpoint;
import org.yamcs.tctm.ccsds.srs4.Srs4Config.Ipv4UdpSettings;

final class Srs4Ipv4UdpHeaderCodec {
    static final int IPV4_HEADER_LENGTH = 20;
    static final int UDP_HEADER_LENGTH = 8;
    static final int HEADER_LENGTH = IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH;
    private static final int UDP_PROTOCOL = 17;

    record DecodedIpv4UdpFrame(byte[] data, int offset, int length, int virtualChannelId) {
    }

    private final Ipv4UdpSettings settings;
    private final Map<Ipv4Endpoint, Integer> sourceRoutes = new HashMap<>();

    Srs4Ipv4UdpHeaderCodec(Ipv4UdpSettings settings) {
        this.settings = settings;
    }

    void addSourceRoute(Ipv4Endpoint endpoint, int vcId) {
        Integer previous = sourceRoutes.putIfAbsent(endpoint, vcId);
        if (previous != null && previous != vcId) {
            throw new IllegalArgumentException("Duplicate SRS4 IPv4/UDP source endpoint for vcId " + previous
                    + " and vcId " + vcId);
        }
    }

    byte[] encode(Ipv4Endpoint destination, byte[] payload) {
        int totalLength = HEADER_LENGTH + payload.length;
        if (totalLength > 0xFFFF) {
            throw new IllegalArgumentException("SRS4 IPv4 packet exceeds 65535 bytes");
        }
        byte[] result = new byte[totalLength];
        result[0] = 0x45;
        result[1] = 0;
        ByteArrayUtils.encodeUnsignedShort(totalLength, result, 2);
        ByteArrayUtils.encodeUnsignedShort(0, result, 4); // Identification
        ByteArrayUtils.encodeUnsignedShort(0, result, 6); // Flags and fragment offset
        result[8] = (byte) settings.ttl();
        result[9] = UDP_PROTOCOL;
        ByteArrayUtils.encodeInt(settings.fixedEndpoint().address(), result, 12);
        ByteArrayUtils.encodeInt(destination.address(), result, 16);
        ByteArrayUtils.encodeUnsignedShort(checksum(result, 0, IPV4_HEADER_LENGTH, 0), result, 10);

        int udpOffset = IPV4_HEADER_LENGTH;
        ByteArrayUtils.encodeUnsignedShort(settings.fixedEndpoint().port(), result, udpOffset);
        ByteArrayUtils.encodeUnsignedShort(destination.port(), result, udpOffset + 2);
        ByteArrayUtils.encodeUnsignedShort(UDP_HEADER_LENGTH + payload.length, result, udpOffset + 4);
        System.arraycopy(payload, 0, result, HEADER_LENGTH, payload.length);
        if (settings.calculateUdpChecksum()) {
            int udpChecksum = udpChecksum(result, 0, totalLength);
            ByteArrayUtils.encodeUnsignedShort(udpChecksum == 0 ? 0xFFFF : udpChecksum, result, udpOffset + 6);
        }
        return result;
    }

    DecodedIpv4UdpFrame decode(byte[] data, int offset, int length) throws TcTmException {
        if (length < HEADER_LENGTH) {
            throw new TcTmException("SRS4 IPv4/UDP frame is shorter than 28 bytes");
        }
        if ((data[offset] & 0xFF) != 0x45) {
            throw new TcTmException("SRS4 requires IPv4 without options (version 4, IHL 5)");
        }
        if ((data[offset + 1] & 0xFF) != 0) {
            throw new TcTmException("Unexpected SRS4 IPv4 DSCP/ECN value");
        }
        if (ByteArrayUtils.decodeUnsignedShort(data, offset + 2) != length) {
            throw new TcTmException("SRS4 IPv4 total length does not match received frame");
        }
        if (ByteArrayUtils.decodeUnsignedShort(data, offset + 6) != 0) {
            throw new TcTmException("Fragmented SRS4 IPv4 packets are not supported");
        }
        if ((data[offset + 8] & 0xFF) != settings.ttl() || (data[offset + 9] & 0xFF) != UDP_PROTOCOL) {
            throw new TcTmException("Unexpected SRS4 IPv4 TTL or protocol");
        }
        if (checksum(data, offset, IPV4_HEADER_LENGTH, 0) != 0) {
            throw new TcTmException("Invalid SRS4 IPv4 header checksum");
        }

        int sourceAddress = ByteArrayUtils.decodeInt(data, offset + 12);
        int destinationAddress = ByteArrayUtils.decodeInt(data, offset + 16);
        int udpOffset = offset + IPV4_HEADER_LENGTH;
        int sourcePort = ByteArrayUtils.decodeUnsignedShort(data, udpOffset);
        int destinationPort = ByteArrayUtils.decodeUnsignedShort(data, udpOffset + 2);
        int udpLength = ByteArrayUtils.decodeUnsignedShort(data, udpOffset + 4);
        if (udpLength != length - IPV4_HEADER_LENGTH) {
            throw new TcTmException("SRS4 UDP length does not match IPv4 payload length");
        }
        Ipv4Endpoint destination = settings.fixedEndpoint();
        if (destinationAddress != destination.address() || destinationPort != destination.port()) {
            throw new TcTmException("Unexpected SRS4 IPv4/UDP destination endpoint");
        }
        int receivedChecksum = ByteArrayUtils.decodeUnsignedShort(data, udpOffset + 6);
        if (receivedChecksum != 0 && udpChecksum(data, offset, length) != 0) {
            throw new TcTmException("Invalid SRS4 UDP checksum");
        }
        Integer vcId = sourceRoutes.get(new Ipv4Endpoint(sourceAddress, sourcePort));
        if (vcId == null) {
            throw new TcTmException("Unknown SRS4 IPv4/UDP source endpoint");
        }
        return new DecodedIpv4UdpFrame(data, offset + HEADER_LENGTH, length - HEADER_LENGTH, vcId);
    }

    private static int udpChecksum(byte[] data, int ipOffset, int totalLength) {
        int udpLength = totalLength - IPV4_HEADER_LENGTH;
        long sum = 0;
        sum = sumWords(data, ipOffset + 12, 8, sum);
        sum += UDP_PROTOCOL;
        sum += udpLength;
        sum = sumWords(data, ipOffset + IPV4_HEADER_LENGTH, udpLength, sum);
        return finishChecksum(sum);
    }

    private static int checksum(byte[] data, int offset, int length, long initialSum) {
        return finishChecksum(sumWords(data, offset, length, initialSum));
    }

    private static long sumWords(byte[] data, int offset, int length, long sum) {
        int end = offset + length;
        while (offset + 1 < end) {
            sum += ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            offset += 2;
        }
        if (offset < end) {
            sum += (data[offset] & 0xFF) << 8;
        }
        return sum;
    }

    private static int finishChecksum(long sum) {
        while ((sum >>> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >>> 16);
        }
        return (int) (~sum) & 0xFFFF;
    }

}
