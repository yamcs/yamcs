package org.yamcs.tctm;

import org.yamcs.archive.PacketWithTime;

/**
 * Used by the {@link TmPacketDataLink} to propagate packets inside Yamcs.
 *  
 * @author nm
 *
 */
public interface TmSink {
    public void processPacket(PacketWithTime pwrt);
}

