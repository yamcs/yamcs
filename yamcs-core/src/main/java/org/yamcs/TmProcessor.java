package org.yamcs;

import org.yamcs.archive.PacketWithTime;

public interface TmProcessor {
    public void processPacket(PacketWithTime pwrt);
    public void finished();
}
