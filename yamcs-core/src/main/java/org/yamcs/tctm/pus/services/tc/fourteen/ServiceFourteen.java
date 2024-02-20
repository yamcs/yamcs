package org.yamcs.tctm.pus.services.tc.fourteen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;

public class ServiceFourteen implements PusService {
    private String yamcsInstance;

    Map<Integer, PusSubService> subServices = new HashMap<>();
    YConfiguration config;

    public ServiceFourteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        subServices.put(1, new SubServiceOne(yamcsInstance, config.getConfigOrEmpty("one")));
        subServices.put(2, new SubServiceTwo(yamcsInstance, config.getConfigOrEmpty("two")));
        subServices.put(3, new SubServiceThree(yamcsInstance, config.getConfigOrEmpty("three")));
        subServices.put(5, new SubServiceFive(yamcsInstance, config.getConfigOrEmpty("five")));
        subServices.put(6, new SubServiceSix(yamcsInstance, config.getConfigOrEmpty("six")));
        subServices.put(7, new SubServiceSeven(yamcsInstance, config.getConfigOrEmpty("seven")));
        subServices.put(9, new SubServiceNine(yamcsInstance, config.getConfigOrEmpty("nine")));
        subServices.put(10, new SubServiceTen(yamcsInstance, config.getConfigOrEmpty("ten")));
        subServices.put(11, new SubServiceEleven(yamcsInstance, config.getConfigOrEmpty("ten")));
        subServices.put(13, new SubServiceThirteen(yamcsInstance, config.getConfigOrEmpty("thirteen")));
        subServices.put(14, new SubServiceFourteen(yamcsInstance, config.getConfigOrEmpty("fourteen")));
        subServices.put(15, new SubServiceFifteen(yamcsInstance, config.getConfigOrEmpty("fifteen")));
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
