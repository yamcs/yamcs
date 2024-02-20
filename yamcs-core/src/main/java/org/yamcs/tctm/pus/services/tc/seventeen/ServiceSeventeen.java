package org.yamcs.tctm.pus.services.tc.seventeen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;

public class ServiceSeventeen implements PusService {
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    private String yamcsInstance;
    YConfiguration config;

    public ServiceSeventeen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, config.getConfigOrEmpty("one")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, config.getConfigOrEmpty("three")));
    }
    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return pusSubServices.get(PusTcCcsdsPacket.getMessageSubType(telecommand)).process(telecommand);
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractPusModifiers'");
    }
    
}
