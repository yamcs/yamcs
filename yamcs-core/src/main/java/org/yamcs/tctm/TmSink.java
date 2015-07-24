package org.yamcs.tctm;

import org.yamcs.archive.PacketWithTime;

/**
 * Used by the TmPacketProviders to propagate packets inside Yamcs.
 * Currently the only (non test) implementation is in the TmProviderAdapter which puts the packet into streams. 
 * 
 * Could be used for creating alternate processing without using Yamcs streams.
 *  
 * @author nm
 *
 */
public interface TmSink {
    public void processPacket(PacketWithTime pwrt);
}

