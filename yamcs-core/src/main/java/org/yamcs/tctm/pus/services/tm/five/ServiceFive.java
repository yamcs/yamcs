package org.yamcs.tctm.pus.services.tm.five;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmModifier;

public class ServiceFive implements PusService {
    Log log;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceFiveConfig;
    private String yamcsInstance;

    public ServiceFive(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceFiveConfig = config;
        initializeSubServices();
    }

    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("two")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("three")));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("four")));
        pusSubServices.put(8, new SubServiceEight(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("eight")));
    }

    @Override
    public TmPacket extractPusModifiers(TmPacket tmPacket) {
        return pusSubServices.get(PusTmModifier.getMessageSubType(tmPacket)).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}
