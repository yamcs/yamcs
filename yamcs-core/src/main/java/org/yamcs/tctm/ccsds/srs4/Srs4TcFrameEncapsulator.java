package org.yamcs.tctm.ccsds.srs4;

import java.util.Collection;

import org.yamcs.CommandOption;
import org.yamcs.CommandOption.CommandOptionType;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.ccsds.TcFrameEncapsulator;
import org.yamcs.tctm.ccsds.UplinkTransferFrame;

/** SRS4 outer-frame encoder for CCSDS telecommand frames. */
public class Srs4TcFrameEncapsulator implements TcFrameEncapsulator {
    public static final CommandOption OPTION_USE_CAN = new CommandOption("useCan", "Use CAN contingency flow",
            CommandOptionType.BOOLEAN).withHelp("Route this command through the SRS4 CAN/CSP contingency flow");

    private final Srs4Config config;
    private final Srs4RadioHeaderCodec radioCodec;
    private final Srs4CspHeaderCodec cspCodec;
    private final Srs4Ipv4UdpHeaderCodec ipv4UdpCodec;

    public Srs4TcFrameEncapsulator(YConfiguration args) {
        config = Srs4Config.forTc(args);
        radioCodec = new Srs4RadioHeaderCodec(config.radioSpacecraftId);
        cspCodec = config.csp.enabled() ? new Srs4CspHeaderCodec(config.csp) : null;
        ipv4UdpCodec = config.ipv4Udp.enabled() ? new Srs4Ipv4UdpHeaderCodec(config.ipv4Udp) : null;
        if (config.dualFlow) {
            var yamcs = YamcsServer.getServer();
            if (!yamcs.hasCommandOption(OPTION_USE_CAN.getId())) {
                yamcs.addCommandOption(OPTION_USE_CAN);
            }
        }
    }

    @Override
    public Object getAggregationKey(PreparedCommand command) {
        return config.dualFlow ? flowFor(command) : config.fixedFlow;
    }

    @Override
    public byte[] encapsulate(UplinkTransferFrame frame) {
        Srs4Flow flow = flowFor(frame);
        Srs4Config.Route route = config.routes.get(frame.getVirtualChannelId());
        if (route == null) {
            throw new IllegalArgumentException("No SRS4 route configured for vcId " + frame.getVirtualChannelId());
        }

        byte[] data = frame.getData();
        if (flow == Srs4Flow.CAN) {
            if (cspCodec == null) {
                throw new IllegalArgumentException("SRS4 CAN flow selected but CSP is disabled");
            }
            data = cspCodec.encode(route.csp(), data);
        } else {
            if (ipv4UdpCodec == null) {
                throw new IllegalArgumentException("SRS4 Ethernet flow selected but IPv4/UDP is disabled");
            }
            data = ipv4UdpCodec.encode(route.ipv4Udp(), data);
        }
        return radioCodec.encode(flow, data);
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
        int radioContentLength = Srs4RadioHeaderCodec.SPACECRAFT_ID_LENGTH + busOverhead + maximumFrameLength;
        if (radioContentLength > Srs4RadioHeaderCodec.MAX_CONTENT_LENGTH) {
            throw new ConfigurationException("SRS4 radio length field cannot contain maximum CCSDS frame length "
                    + maximumFrameLength + " plus " + busOverhead + " bytes of bus header");
        }
    }

    private Srs4Flow flowFor(UplinkTransferFrame frame) {
        if (!config.dualFlow) {
            return config.fixedFlow;
        }
        if (frame.isCmdControl() || frame.getCommands() == null || frame.getCommands().isEmpty()) {
            return config.controlFrameFlow;
        }
        return flowFor(frame.getCommands().get(0));
    }

    private Srs4Flow flowFor(PreparedCommand command) {
        Boolean useCan = command.getBooleanAttribute(OPTION_USE_CAN.getId());
        return Boolean.TRUE.equals(useCan) ? Srs4Flow.CAN : Srs4Flow.ETHERNET;
    }
}
