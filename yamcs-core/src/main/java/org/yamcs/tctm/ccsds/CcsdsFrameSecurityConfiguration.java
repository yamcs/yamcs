package org.yamcs.tctm.ccsds;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.DataLinkComponent;

/** Supplies CCSDS-aware security associations and per-VC SPI selection. */
public interface CcsdsFrameSecurityConfiguration extends DataLinkComponent {

    YConfiguration applyTo(YConfiguration protocolConfiguration);

    boolean isTelecommand();
}
