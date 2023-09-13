package org.yamcs.tctm.pus.services.tm.two;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public class ServiceTwo implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();

    String yamcsInstance;
    YConfiguration serviceTwoConfig;

    public ServiceTwo(String yamcsInstance, YConfiguration serviceTwoConfig) {
        this.yamcsInstance = yamcsInstance;
        this.serviceTwoConfig = serviceTwoConfig;

        initializeSubServices();    
    }

    public void initializeSubServices() {
        pusSubServices.put(6, new SubServiceSix(yamcsInstance, serviceTwoConfig.getConfigOrEmpty("six")));
    }

    public void acceptPusPacket(PusTmPacket pusTmPacket) {
        pusSubServices.get(pusTmPacket.getMessageSubType()).process(pusTmPacket);
    }
}
