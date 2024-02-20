package org.yamcs.tctm.pus.services.tc.fifteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ServiceFifteen implements PusService {
    Log log;
    private final String yamcsInstance;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceFiveConfig;

    public ServiceFifteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceFiveConfig = config;
        
        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("two")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("three")));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("four")));
        pusSubServices.put(5, new SubServiceFive(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("five")));
        pusSubServices.put(9, new SubServiceNine(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("nine")));
        pusSubServices.put(11, new SubServiceEleven(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("eleven")));
        pusSubServices.put(12, new SubServiceTwelve(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twelve")));
        pusSubServices.put(14, new SubServiceFourteen(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("fourteen")));
        pusSubServices.put(15, new SubServiceFifteen(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("fifteen")));
        pusSubServices.put(16, new SubServiceSixteen(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("sixteen")));
        pusSubServices.put(17, new SubServiceSeventeen(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("seventeen")));
        pusSubServices.put(18, new SubServiceEighteen(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("eighteen")));
        pusSubServices.put(20, new SubServiceTwenty(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twenty")));
        pusSubServices.put(21, new SubServiceTwentyOne(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentyOne")));
        pusSubServices.put(22, new SubServiceTwentyTwo(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentyTwo")));
        pusSubServices.put(24, new SubServiceTwentyFour(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentyFour")));
        pusSubServices.put(25, new SubServiceTwentyFive(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentyFive")));
        pusSubServices.put(26, new SubServiceTwentySix(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentySix")));
        pusSubServices.put(27, new SubServiceTwentySeven(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentySeven")));
        pusSubServices.put(28, new SubServiceTwentyEight(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentyEight")));
        pusSubServices.put(29, new SubServiceTwentyNine(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentyNine")));
        pusSubServices.put(30, new SubServiceThirty(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirty")));
        pusSubServices.put(31, new SubServiceThirtyOne(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtyOne")));
        pusSubServices.put(32, new SubServiceThirtyTwo(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtyTwo")));
        pusSubServices.put(33, new SubServiceThirtyThree(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtyThree")));
        pusSubServices.put(34, new SubServiceThirtyFour(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtyFour")));
        pusSubServices.put(35, new SubServiceThirtyFive(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtyFive")));
        pusSubServices.put(37, new SubServiceThirtySeven(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtySeven")));
        pusSubServices.put(39, new SubServiceThirtyNine(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtyNine")));
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return null;
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return null;
    }
}
