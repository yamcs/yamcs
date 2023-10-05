package org.yamcs.tctm.pus.services.tc.two;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcModifier;

public class ServiceTwo implements PusService {
    Log log;
    private String yamcsInstance;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceTwoConfig;

    public ServiceTwo(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceTwoConfig = config;
        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("two")));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("four")));
        pusSubServices.put(5, new SubServiceFive(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("five")));
        pusSubServices.put(7, new SubServiceSeven(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("seven")));
        pusSubServices.put(8, new SubServiceEight(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("eight")));
        pusSubServices.put(10, new SubServiceTen(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("ten")));
        pusSubServices.put(11, new SubServiceEleven(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("eleven")));
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return pusSubServices.get(PusTcModifier.getMessageSubType(telecommand)).process(telecommand);
    }

    @Override
    public TmPacket extractPusModifiers(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'acceptPusPacket'");
    }

}
