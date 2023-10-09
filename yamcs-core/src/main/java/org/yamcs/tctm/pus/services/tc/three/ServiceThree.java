package org.yamcs.tctm.pus.services.tc.three;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcModifier;
import org.yamcs.tctm.pus.services.tc.two.SubServiceEleven;

public class ServiceThree implements PusService  {
    Log log;
    private String yamcsInstance;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceThreeConfig;

    public ServiceThree(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceThreeConfig = config;
        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(5, new SubServiceFive(yamcsInstance, serviceThreeConfig.getConfigOrEmpty("five")));
        pusSubServices.put(6, new SubServiceSix(yamcsInstance, serviceThreeConfig.getConfigOrEmpty("six")));
        pusSubServices.put(7, new SubServiceSeven(yamcsInstance, serviceThreeConfig.getConfigOrEmpty("seven")));
        pusSubServices.put(8, new SubServiceEight(yamcsInstance, serviceThreeConfig.getConfigOrEmpty("eight")));
        pusSubServices.put(37, new SubServiceThirtySeven(yamcsInstance, serviceThreeConfig.getConfigOrEmpty("thirtySeven")));
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
