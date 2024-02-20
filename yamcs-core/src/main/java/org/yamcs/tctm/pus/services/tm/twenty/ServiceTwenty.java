package org.yamcs.tctm.pus.services.tm.twenty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class ServiceTwenty implements PusService {
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    private String yamcsInstance;
    YConfiguration config;

    public ServiceTwenty(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, config.getConfigOrEmpty("two")));
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return pusSubServices.get(PusTmCcsdsPacket.getMessageSubType(tmPacket.getPacket())).process(tmPacket);
    }
}
