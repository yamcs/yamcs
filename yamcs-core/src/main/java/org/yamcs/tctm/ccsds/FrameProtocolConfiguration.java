package org.yamcs.tctm.ccsds;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.DataLinkComponent;

/** Configuration-bearing CCSDS protocol component used by a composed frame link. */
public interface FrameProtocolConfiguration extends DataLinkComponent {

    YConfiguration configuration();

    boolean isTelecommand();
}
