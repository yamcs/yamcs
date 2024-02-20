package org.yamcs.tctm.pus.services.tc.fifteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;

import java.util.ArrayList;

public class SubServiceFourteen implements PusSubService {
    String yamcsInstance;
    YConfiguration config;

    SubServiceFourteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        return PusTcCcsdsPacket.setPusHeadersSpareFieldAndSourceID(telecommand);
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");    }
}
