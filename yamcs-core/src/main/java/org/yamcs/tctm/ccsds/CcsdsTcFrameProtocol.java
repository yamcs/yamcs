package org.yamcs.tctm.ccsds;

import org.yamcs.YConfiguration;

/** CCSDS telecommand frame protocol configuration component. */
public class CcsdsTcFrameProtocol implements FrameProtocolConfiguration {
    private YConfiguration config;

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration args) {
        config = args;
    }

    @Override
    public YConfiguration configuration() {
        return config;
    }

    @Override
    public boolean isTelecommand() {
        return true;
    }
}
