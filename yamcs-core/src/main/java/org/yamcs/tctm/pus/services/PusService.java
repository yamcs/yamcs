package org.yamcs.tctm.pus.services;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;

public interface PusService {
    void initializeSubServices();
    PreparedCommand addPusModifiers(PreparedCommand telecommand);
    TmPacket extractPusModifiers(TmPacket tmPacket);
}
