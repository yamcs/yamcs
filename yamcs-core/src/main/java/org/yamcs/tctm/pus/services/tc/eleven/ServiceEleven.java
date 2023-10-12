package org.yamcs.tctm.pus.services.tc.eleven;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcModifier;

public class ServiceEleven implements PusService{
    Log log;
    private String yamcsInstance;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceElevenConfig;

    public ServiceEleven(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceElevenConfig = config;

        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("two")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("three")));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("four")));
        pusSubServices.put(5, new SubServiceFive(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("five")));
        pusSubServices.put(6, new SubServiceSix(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("six")));
        pusSubServices.put(16, new SubServiceSixteen(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("sixteen")));
        pusSubServices.put(17, new SubServiceSeventeen(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("seventeen")));
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
