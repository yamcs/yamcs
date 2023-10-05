package org.yamcs.tctm.pus.services.tc.two;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;

public class SubServiceFour implements PusSubService {
    String yamcsInstance;

    SubServiceFour(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
}
