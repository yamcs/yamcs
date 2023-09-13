package org.yamcs.tctm.pus.services.tm;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.tm.one.ServiceOne;
import org.yamcs.tctm.pus.services.tm.two.ServiceTwo;
import org.yamcs.tctm.pus.services.tm.three.ServiceThree;
import org.yamcs.tctm.pus.services.tm.four.ServiceFour;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.tm.five.ServiceFive;

public class TmPusManager {
    Log log;
    final String yamcsInstance;

    Map<Integer, PusService> pusServices = new HashMap<>();
    YConfiguration pusServicesConfig;

    public TmPusManager(String instanceName, YConfiguration pusServicesConfig) {
        this.yamcsInstance = instanceName;
        this.pusServicesConfig = pusServicesConfig;

        initializePUSServices();
    }

    private void initializePUSServices() {
        pusServices.put(1, new ServiceOne(yamcsInstance));
        pusServices.put(2, new ServiceTwo(yamcsInstance, pusServicesConfig.getConfigOrEmpty("three")));
        pusServices.put(3, new ServiceThree(yamcsInstance));
        pusServices.put(4, new ServiceFour(yamcsInstance));
        pusServices.put(5, new ServiceFive(yamcsInstance));
    }

    public void acceptTmPacket(TmPacket tmPacket) {
        PusTmPacket pusTmPacket = new PusTmPacket(tmPacket);
        pusServices.get(pusTmPacket.getMessageType()).acceptPusPacket(pusTmPacket);
    }
}
