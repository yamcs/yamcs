package org.yamcs.tctm.pus.services;

import java.util.ArrayList;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;

public interface PusSubService {
    PreparedCommand process(PreparedCommand telecommand);
    ArrayList<TmPacket> process(TmPacket tmPacket);
}
