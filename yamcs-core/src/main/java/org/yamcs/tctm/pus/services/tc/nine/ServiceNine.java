package org.yamcs.tctm.pus.services.tc.nine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;

public class ServiceNine implements PusService {
    private String yamcsInstance;

    Map<Integer, PusSubService> subServices = new HashMap<>();
    YConfiguration config;

    public ServiceNine(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        subServices.put(1, new SubServiceOne(yamcsInstance, config.getConfigOrEmpty("one")));
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return subServices.get(PusTcCcsdsPacket.getMessageSubType(telecommand)).process(telecommand);
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractPusModifiers'");
    }
}
