package org.yamcs.tctm.ccsds.srs4;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;

/** Builds the schema for SRS4 provider arguments embedded in a frame-link spec. */
public final class Srs4ConfigSpec {

    public static final String TC_CLASS = "org.yamcs.tctm.ccsds.srs4.Srs4TcFrameEncapsulator";
    public static final String TM_CLASS = "org.yamcs.tctm.ccsds.srs4.Srs4TmFrameDecapsulator";

    private Srs4ConfigSpec() {
    }

    /**
     * Returns a provider-argument spec which validates the known {@code args.srs4} block while allowing other
     * provider-specific arguments at the same level.
     */
    public static Spec providerArgsSpec(boolean tc) {
        Spec args = new Spec();
        args.addOption("srs4", OptionType.MAP).withSpec(srs4Spec(tc));
        args.allowUnknownKeys(true);
        return args;
    }

    private static Spec srs4Spec(boolean tc) {
        Spec radio = new Spec();
        radio.addOption("enabled", OptionType.BOOLEAN).withDefault(true);
        radio.addOption("spacecraftId", OptionType.INTEGER).withRequired(true);

        String fixedCspAddress = tc ? "sourceAddress" : "destinationAddress";
        String fixedCspPort = tc ? "sourcePort" : "destinationPort";
        Spec csp = new Spec();
        csp.addOption("enabled", OptionType.BOOLEAN).withDefault(true);
        csp.addOption(fixedCspAddress, OptionType.INTEGER);
        csp.addOption(fixedCspPort, OptionType.INTEGER);
        csp.addOption("priority", OptionType.INTEGER).withDefault(0);
        csp.addOption("hmac", OptionType.BOOLEAN).withDefault(false);
        csp.addOption("xtea", OptionType.BOOLEAN).withDefault(false);
        csp.addOption("rdp", OptionType.BOOLEAN).withDefault(false);
        csp.addOption("crc", OptionType.BOOLEAN).withDefault(false);
        csp.when("enabled", true).requireAll(fixedCspAddress, fixedCspPort);

        String fixedIpv4Address = tc ? "sourceAddress" : "destinationAddress";
        String fixedIpv4Port = tc ? "sourcePort" : "destinationPort";
        Spec ipv4Udp = new Spec();
        ipv4Udp.addOption("enabled", OptionType.BOOLEAN).withDefault(true);
        ipv4Udp.addOption(fixedIpv4Address, OptionType.STRING);
        ipv4Udp.addOption(fixedIpv4Port, OptionType.INTEGER);
        ipv4Udp.addOption("ttl", OptionType.INTEGER).withDefault(64);
        ipv4Udp.addOption("calculateUdpChecksum", OptionType.BOOLEAN).withDefault(false);
        ipv4Udp.when("enabled", true).requireAll(fixedIpv4Address, fixedIpv4Port);

        Spec routeCsp = new Spec();
        routeCsp.addOption(tc ? "destinationAddress" : "sourceAddress", OptionType.INTEGER)
                .withRequired(true);
        routeCsp.addOption(tc ? "destinationPort" : "sourcePort", OptionType.INTEGER)
                .withRequired(true);

        Spec routeIpv4Udp = new Spec();
        routeIpv4Udp.addOption(tc ? "destinationAddress" : "sourceAddress", OptionType.STRING)
                .withRequired(true);
        routeIpv4Udp.addOption(tc ? "destinationPort" : "sourcePort", OptionType.INTEGER)
                .withRequired(true);

        Spec route = new Spec();
        route.addOption("vcId", OptionType.INTEGER).withRequired(true);
        route.addOption("csp", OptionType.MAP).withSpec(routeCsp);
        route.addOption("ipv4Udp", OptionType.MAP).withSpec(routeIpv4Udp);

        Spec srs4 = new Spec();
        srs4.addOption("radio", OptionType.MAP).withSpec(radio).withRequired(true);
        srs4.addOption("csp", OptionType.MAP).withSpec(csp);
        srs4.addOption("ipv4Udp", OptionType.MAP).withSpec(ipv4Udp);
        srs4.addOption("controlFrameFlow", OptionType.STRING)
                .withChoices(Srs4Flow.class)
                .withDefault(Srs4Flow.ETHERNET);
        srs4.addOption("virtualChannels", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(route);
        return srs4;
    }
}
