package org.yamcs.tctm.pus.services.tm.fifteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;

import java.util.ArrayList;

public class SubServiceNineteen implements PusSubService {
    String yamcsInstance;
    YConfiguration config;


    public SubServiceNineteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }
    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        ArrayList<TmPacket> pusPackets = new ArrayList<>();
        pusPackets.add(tmPacket);

        return pusPackets;
    }
}
