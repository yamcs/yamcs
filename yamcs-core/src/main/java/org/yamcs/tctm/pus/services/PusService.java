package org.yamcs.tctm.pus.services;

import java.util.ArrayList;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;

public interface PusService {
    void initializeSubServices();
    PreparedCommand addPusModifiers(PreparedCommand telecommand);
    ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket);
}
