package org.yamcs.tctm.pus.services;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public interface PusService {
    void initializeSubServices();
    PreparedCommand addPusModifiers(PreparedCommand telecommand);
    TmPacket acceptPusPacket(PusTmPacket pusTmPacket);
}
