package org.yamcs.tctm.ccsds;

/** SDLS configuration for a CCSDS telemetry protocol component. */
public class CcsdsTmSdlsConfiguration extends AbstractCcsdsSdlsConfiguration {
    @Override
    public boolean isTelecommand() {
        return false;
    }
}
