package org.yamcs.tctm.pus.services.tc.two;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcModifier;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public class SubServiceFive implements PusSubService {
    String yamcsInstance;

    SubServiceFive(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        return PusTcModifier.setPusHeadersSpareFieldAndSourceID(telecommand);
    }

    @Override
    public TmPacket process(PusTmPacket pusTmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
    
}
