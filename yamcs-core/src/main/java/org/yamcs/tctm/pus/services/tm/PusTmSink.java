package org.yamcs.tctm.pus.services.tm;

import org.yamcs.TmPacket;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.yarch.Stream;

/**
 * Used by the {@link PusTmManager} to publish extracted PUS Packages into the correct streams.
 *  
 */
public interface PusTmSink {
    public void processPusPacket(TmPacket tmPacket, Stream stream, String tmLinkName);
}
