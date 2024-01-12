package org.yamcs.tctm.pus.services;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.yarch.Stream;

/**
 * Used by the {@link PusTmManager} to publish extracted PUS Packages into the correct streams.
 *  
 */
public interface PusSink {
    public void emitTmTuple(TmPacket tmPacket, Stream stream, String tmLinkName);
    public void emitTcTuple(PreparedCommand pc, Stream stream);
}
