package org.yamcs.tctm.pus.services;

import org.yamcs.TmPacket;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public interface PusService {
    void initializeSubServices();
    TmPacket acceptPusPacket(PusTmPacket pusTmPacket);
}
