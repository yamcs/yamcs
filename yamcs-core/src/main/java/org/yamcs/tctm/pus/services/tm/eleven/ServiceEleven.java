package org.yamcs.tctm.pus.services.tm.eleven;

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

public class ServiceEleven implements PusService {
    Log log;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceElevenConfig;
    private String yamcsInstance;

    public ServiceEleven(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceElevenConfig = config;
        initializeSubServices();
    }

    public void initializeSubServices() {
        pusSubServices.put(10, new SubServiceTen(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("ten")));
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return pusSubServices.get(PusTmCcsdsPacket.getMessageSubType(tmPacket.getPacket())).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}
