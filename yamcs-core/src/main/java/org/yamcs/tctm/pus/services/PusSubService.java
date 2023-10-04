package org.yamcs.tctm.pus.services;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public interface PusSubService {
    PreparedCommand process(PreparedCommand pusTelecommand);
    TmPacket process(PusTmPacket pusTmPacket);
}
