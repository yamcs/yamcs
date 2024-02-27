package org.yamcs.tctm.pus.services.tc.six;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;

public class ServiceSix implements PusService {
    Log log;
    private final String yamcsInstance;

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
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, serviceSixConfig.getConfigOrEmpty("three")));
        pusSubServices.put(7, new SubServiceSeven(yamcsInstance, serviceSixConfig.getConfigOrEmpty("seven")));
        pusSubServices.put(12, new SubServiceTwelve(yamcsInstance, serviceSixConfig.getConfigOrEmpty("twelve")));
        pusSubServices.put(15, new SubServiceFifteen(yamcsInstance, serviceSixConfig.getConfigOrEmpty("fifteen")));
        pusSubServices.put(16, new SubServiceSixteen(yamcsInstance, serviceSixConfig.getConfigOrEmpty("sixteen")));
        pusSubServices.put(17, new SubServiceSeventeen(yamcsInstance, serviceSixConfig.getConfigOrEmpty("seventeen")));
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
