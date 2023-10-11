package org.yamcs.tctm.pus.services.tc.five;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcModifier;

public class ServiceFive implements PusService {
    Log log;
    private String yamcsInstance;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceFiveConfig;

    public ServiceFive(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceFiveConfig = config;
        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(5, new SubServiceFive(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("five")));
        pusSubServices.put(6, new SubServiceSix(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("six")));
        pusSubServices.put(7, new SubServiceSeven(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("seven")));
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return pusSubServices.get(PusTcModifier.getMessageSubType(telecommand)).process(telecommand);
    }

    @Override
    public TmPacket extractPusModifiers(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractPusModifiers'");
    }
}
