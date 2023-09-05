package org.yamcs.tctm.pus.services;

import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public interface PusSubService {
    void process(PusTmPacket pusTmPacket);
}
