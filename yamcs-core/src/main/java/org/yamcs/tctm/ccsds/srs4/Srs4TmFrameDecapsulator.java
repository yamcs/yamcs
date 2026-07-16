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

        for (var entry : config.routes.entrySet()) {
            if (cspCodec != null) {
                cspCodec.addSourceRoute(entry.getValue().csp(), entry.getKey());
            }
            if (ipv4UdpCodec != null) {
                ipv4UdpCodec.addSourceRoute(entry.getValue().ipv4Udp(), entry.getKey());
            }
        }
    }

    @Override
    public DecapsulatedFrame decapsulate(byte[] data, int offset, int length) throws TcTmException {
        var radioFrame = radioCodec.decode(data, offset, length);
        if (radioFrame.flow() == Srs4Flow.CAN) {
            if (cspCodec == null) {
                throw new TcTmException("SRS4 radio selected CAN but the CSP decoder is disabled");
            }
            var frame = cspCodec.decode(radioFrame.data(), radioFrame.offset(), radioFrame.length());
            return new DecapsulatedFrame(frame.data(), frame.offset(), frame.length(), frame.virtualChannelId());
        } else {
            if (ipv4UdpCodec == null) {
                throw new TcTmException("SRS4 radio selected Ethernet but the IPv4/UDP decoder is disabled");
            }
            var frame = ipv4UdpCodec.decode(radioFrame.data(), radioFrame.offset(), radioFrame.length());
            return new DecapsulatedFrame(frame.data(), frame.offset(), frame.length(), frame.virtualChannelId());
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
            Srs4Config.Route route = config.routes.get(vcId);
            if (route == null || (config.csp.enabled() && route.csp() == null)
                    || (config.ipv4Udp.enabled() && route.ipv4Udp() == null)) {
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
}
