package org.yamcs.tctm.pus.services;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;

public interface PusSubService {
    PreparedCommand process(PreparedCommand telecommand);
    TmPacket process(TmPacket tmPacket);
}
