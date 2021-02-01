package org.yamcs.tctm;

import org.yamcs.TmPacket;

/**
 * Used by the {@link TmPacketDataLink} to propagate packets inside Yamcs.
 *  
 * @author nm
 *
 */
public interface TmSink {
    public void processPacket(TmPacket tmPacket);
}

