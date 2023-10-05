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

public class PusTmManager {
    Log log;
    String yamcsInstance;

    // Static Members
    static int PRIMARY_HEADER_LENGTH = 6;
    static int DEFAULT_SECONDARY_HEADER_LENGTH = 64;
    static int PUS_HEADER_LENGTH = 11;

    static int secondaryHeaderLength;

    Map<Integer, PusService> pusServices = new HashMap<>();
    YConfiguration pusServicesConfig;

    public PusTmManager(String instanceName, YConfiguration pusConfig) {
        this.yamcsInstance = instanceName;

        this.pusServicesConfig = pusConfig.getConfigOrEmpty("services");
        secondaryHeaderLength = pusConfig.getInt("secondaryHeaderLength", DEFAULT_SECONDARY_HEADER_LENGTH);

        initializePUSServices();
    }

    private void initializePUSServices() {
        pusServices.put(1, new ServiceOne(yamcsInstance, pusServicesConfig.getConfigOrEmpty("one")));
        pusServices.put(2, new ServiceTwo(yamcsInstance, pusServicesConfig.getConfigOrEmpty("two")));
        pusServices.put(3, new ServiceThree(yamcsInstance, pusServicesConfig.getConfigOrEmpty("three")));
        pusServices.put(4, new ServiceFour(yamcsInstance, pusServicesConfig.getConfigOrEmpty("four")));
        pusServices.put(5, new ServiceFive(yamcsInstance, pusServicesConfig.getConfigOrEmpty("five")));
    }

    public TmPacket acceptTmPacket(TmPacket tmPacket) {
        return pusServices.get(PusTmModifier.getMessageType(tmPacket)).extractPusModifiers(tmPacket);
    }
}
