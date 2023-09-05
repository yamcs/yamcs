package org.yamcs.tctm.pus.services;

import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public interface PusService {
    void initializeSubServices();
    void acceptPusPacket(PusTmPacket pusTmPacket);
}
