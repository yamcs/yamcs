package org.yamcs.tctm.ccsds;

/** SDLS configuration for a CCSDS telecommand protocol component. */
public class CcsdsTcSdlsConfiguration extends AbstractCcsdsSdlsConfiguration {
    @Override
    public boolean isTelecommand() {
        return true;
    }
}
