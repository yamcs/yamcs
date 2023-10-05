package org.yamcs.tctm.pus.services;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;

public interface PusSubService {
    PreparedCommand process(PreparedCommand pusTelecommand);
    TmPacket process(TmPacket tmPacket);
}
