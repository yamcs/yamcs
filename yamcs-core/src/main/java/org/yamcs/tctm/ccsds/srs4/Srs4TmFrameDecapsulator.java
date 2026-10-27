package org.yamcs.tctm.ccsds.srs4;

import java.util.Collection;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.TmFrameDecapsulator;

/** SRS4 outer-frame decoder for CCSDS telemetry frames. */
public class Srs4TmFrameDecapsulator implements TmFrameDecapsulator {
    private final Srs4Config config;
    private final Srs4RadioHeaderCodec radioCodec;
    private final Srs4CspHeaderCodec cspCodec;
    private final Srs4Ipv4UdpHeaderCodec ipv4UdpCodec;

    public Srs4TmFrameDecapsulator(YConfiguration args) {
        config = Srs4Config.forTm(args);
        radioCodec = new Srs4RadioHeaderCodec(config.radioSpacecraftId);
        cspCodec = config.csp.enabled() ? new Srs4CspHeaderCodec(config.csp) : null;
        ipv4UdpCodec = config.ipv4Udp.enabled() ? new Srs4Ipv4UdpHeaderCodec(config.ipv4Udp) : null;

        for (var route : config.tmRoutes) {
            for (int vcId : route.vcIds()) {
                if (cspCodec != null) {
                    for (int sourceAddress : route.cspSourceAddresses()) {
                        cspCodec.addSourceRoute(sourceAddress, vcId);
                    }
                }
                if (ipv4UdpCodec != null) {
                    for (var endpoint : route.ipv4Udp()) {
                        ipv4UdpCodec.addSourceRoute(endpoint, vcId);
                    }
                }
            }
        }
    }

    @Override
    public DecapsulatedFrame decapsulate(byte[] data, int offset, int length)
            throws TcTmException {
        var radioFrame = radioCodec.decode(data, offset, length);
        if (radioFrame.flow() == Srs4Flow.CAN) {
            if (cspCodec == null) {
                throw new TcTmException("SRS4 radio selected CAN but the CSP decoder is disabled");
            }
            var frame = cspCodec.decode(radioFrame.data(), radioFrame.offset(), radioFrame.length());
            return new DecapsulatedFrame(frame.data(), frame.offset(), frame.length(), frame.virtualChannelIds());
        } else {
            if (ipv4UdpCodec == null) {
                throw new TcTmException("SRS4 radio selected Ethernet but the IPv4/UDP decoder is disabled");
            }
            var frame = ipv4UdpCodec.decode(radioFrame.data(), radioFrame.offset(), radioFrame.length());
            return new DecapsulatedFrame(frame.data(), frame.offset(), frame.length(), frame.virtualChannelIds());
        }
    }

    @Override
    public int maxFrameOverhead() {
        int busOverhead = config.ipv4Udp.enabled() ? Srs4Ipv4UdpHeaderCodec.HEADER_LENGTH
                : Srs4CspHeaderCodec.HEADER_LENGTH;
        return Srs4RadioHeaderCodec.HEADER_LENGTH + busOverhead;
    }

    @Override
    public void validate(int maximumFrameLength, Collection<Integer> virtualChannelIds) {
        for (int vcId : virtualChannelIds) {
            boolean found = config.tmRoutes.stream().anyMatch(route -> route.vcIds().contains(vcId));
            if (!found) {
                throw new ConfigurationException("Incomplete SRS4 route for configured vcId " + vcId);
            }
        }
        int busOverhead = config.ipv4Udp.enabled() ? Srs4Ipv4UdpHeaderCodec.HEADER_LENGTH
                : Srs4CspHeaderCodec.HEADER_LENGTH;
        if (Srs4RadioHeaderCodec.SPACECRAFT_ID_LENGTH + busOverhead + maximumFrameLength > Srs4RadioHeaderCodec.MAX_CONTENT_LENGTH) {
            throw new ConfigurationException("SRS4 radio length field cannot contain maximum CCSDS frame length "
                    + maximumFrameLength + " plus " + busOverhead + " bytes of bus header");
        }
    }

    @SuppressWarnings("unused")
    private void validateFrameLength(int receivedLength, int expectedInnerFrameLength, int busHeaderLength,
            String flow) throws TcTmException {
        if (expectedInnerFrameLength == -1) {
            return;
        }
        int expectedLength = expectedInnerFrameLength + Srs4RadioHeaderCodec.TYPE_AND_LENGTH_LENGTH
                + Srs4RadioHeaderCodec.SPACECRAFT_ID_LENGTH + busHeaderLength;
        if (receivedLength != expectedLength) {
            throw new TcTmException("Bad SRS4 " + flow + " frame length " + receivedLength + "; expected "
                    + expectedLength);
        }
    }
}
