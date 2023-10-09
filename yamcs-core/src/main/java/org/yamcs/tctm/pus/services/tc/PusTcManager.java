package org.yamcs.tctm.pus.services.tc;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.tc.two.ServiceTwo;
import org.yamcs.tctm.pus.services.tm.three.ServiceThree;

public class PusTcManager {
    Log log;
    String yamcsInstance;

    // Static Members
    static int PRIMARY_HEADER_LENGTH = 6;
    static int DEFAULT_SECONDARY_HEADER_LENGTH = 64;
    static int PUS_HEADER_LENGTH = 5;

    static int secondaryHeaderLength;
    static int sourceID;
    static int secondaryHeaderSpareLength;

    Map<Integer, PusService> pusServices = new HashMap<>();
    YConfiguration pusServicesConfig;

    public PusTcManager(String instanceName, YConfiguration pusConfig) {
        this.yamcsInstance = instanceName;

        this.pusServicesConfig = pusConfig.getConfigOrEmpty("services");
        secondaryHeaderLength = pusConfig.getInt("secondaryHeaderLength", DEFAULT_SECONDARY_HEADER_LENGTH);
        sourceID = pusConfig.getInt("sourceID");

        secondaryHeaderSpareLength = secondaryHeaderLength - PUS_HEADER_LENGTH;

        initializePUSServices();
    }

    private void initializePUSServices() {
        pusServices.put(2, new ServiceTwo(yamcsInstance, pusServicesConfig.getConfigOrEmpty("two")));
        pusServices.put(3, new ServiceThree(yamcsInstance, pusServicesConfig.getConfigOrEmpty("three")));
    }

    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return pusServices.get(PusTcModifier.getMessageType(telecommand)).addPusModifiers(telecommand);
    }
}
