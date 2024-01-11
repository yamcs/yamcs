package org.yamcs.tctm.pus.services.tc.eleven;

import java.util.ArrayList;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcModifier;

public class SubServiceSeventeen implements PusSubService {
    String yamcsInstance;

    SubServiceSeventeen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        return PusTcModifier.setPusHeadersSpareFieldAndSourceID(telecommand);
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

}
