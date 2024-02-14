package org.yamcs.tctm.pus.services.tm.nine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class ServiceNine implements PusService {
    Log log;
    Map<Integer, PusSubService> subServices = new HashMap<>();
    String yamcsInstance;
    YConfiguration config;

    public ServiceNine(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        initializeSubServices();
    }

    public void initializeSubServices() {
        subServices.put(2, new SubServiceTwo(yamcsInstance, config.getConfigOrEmpty("two")));
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return subServices.get(PusTmCcsdsPacket.getMessageSubType(tmPacket.getPacket())).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}
