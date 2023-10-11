package org.yamcs.tctm.pus.services.tc.six;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcModifier;

public class ServiceSix implements PusService {
    Log log;
    private String yamcsInstance;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceSixConfig;

    public ServiceSix(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceSixConfig = config;

        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, serviceSixConfig.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, serviceSixConfig.getConfigOrEmpty("two")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, serviceSixConfig.getConfigOrEmpty("three")));
        pusSubServices.put(5, new SubServiceFive(yamcsInstance, serviceSixConfig.getConfigOrEmpty("five")));
        pusSubServices.put(7, new SubServiceSeven(yamcsInstance, serviceSixConfig.getConfigOrEmpty("seven")));
        pusSubServices.put(9, new SubServiceNine(yamcsInstance, serviceSixConfig.getConfigOrEmpty("nine")));
        pusSubServices.put(12, new SubServiceTwelve(yamcsInstance, serviceSixConfig.getConfigOrEmpty("twelve")));
        pusSubServices.put(15, new SubServiceFifteen(yamcsInstance, serviceSixConfig.getConfigOrEmpty("fifteen")));
        pusSubServices.put(16, new SubServiceSixteen(yamcsInstance, serviceSixConfig.getConfigOrEmpty("sixteen")));
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
