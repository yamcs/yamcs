package org.yamcs.tctm.pus.services;

import org.yamcs.TmPacket;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public interface PusSubService {
    TmPacket process(PusTmPacket pusTmPacket);
}
