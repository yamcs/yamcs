package org.yamcs.tctm.pus.services.tc;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.tc.two.ServiceTwo;
import org.yamcs.tctm.pus.services.tc.eleven.ServiceEleven;
import org.yamcs.tctm.pus.services.tc.three.ServiceThree;


public class PusTcManager {
    Log log;
    String yamcsInstance;

    // Static Members
    public static int DEFAULT_PRIMARY_HEADER_LENGTH = 6;
    public static int DEFAULT_SECONDARY_HEADER_LENGTH = 32;
    public static int DEFAULT_PUS_HEADER_LENGTH = 5;
    public static int DEFAULT_SOURCE_ID = 14;   // FIXME: Not needed

    public static int secondaryHeaderLength;
    public static int sourceID;
    public static int secondaryHeaderSpareLength;

    Map<Integer, PusService> pusServices = new HashMap<>();
    YConfiguration pusServicesConfig;

    public PusTcManager(String instanceName, YConfiguration pusConfig) {
        this.yamcsInstance = instanceName;

        pusServicesConfig = pusConfig.getConfigOrEmpty("services");
        secondaryHeaderLength = pusConfig.getInt("secondaryHeaderLength", DEFAULT_SECONDARY_HEADER_LENGTH);
        sourceID = pusConfig.getInt("sourceID", DEFAULT_SOURCE_ID);
        secondaryHeaderSpareLength = secondaryHeaderLength - DEFAULT_PUS_HEADER_LENGTH;

        initializePUSServices();
    }

    private void initializePUSServices() {
        pusServices.put(2, new ServiceTwo(yamcsInstance, pusServicesConfig.getConfigOrEmpty("two")));
        pusServices.put(3, new ServiceThree(yamcsInstance, pusServicesConfig.getConfigOrEmpty("three")));
        pusServices.put(11, new ServiceEleven(yamcsInstance, pusServicesConfig.getConfigOrEmpty("eleven")));
    }

    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return pusServices.get(PusTcModifier.getMessageType(telecommand)).addPusModifiers(telecommand);
    }

    public PreparedCommand addTimetagModifiers(PreparedCommand telecommand) {
        ServiceEleven serviceEleven = (ServiceEleven) pusServices.get(11);
        return serviceEleven.addTimetagModifiers(telecommand);
    }

    public int getSourceID() {
        return sourceID;
    }

}
