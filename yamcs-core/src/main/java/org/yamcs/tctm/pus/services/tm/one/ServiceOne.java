package org.yamcs.tctm.pus.services.tm.one;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class ServiceOne implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    String yamcsInstance;
    YConfiguration serviceOneConfig;

    public static final int DEFAULT_FAILURE_CODE_SIZE = 1;
    public static final int DEFAULT_FAILURE_DATA_SIZE = 4;
    public static final int REQUEST_ID_LENGTH = 6;

    public static int failureCodeSize;
    public static int failureDataSize;

    public ServiceOne(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceOneConfig = config;

        failureCodeSize = config.getInt("failureCodeSize", DEFAULT_FAILURE_CODE_SIZE);
        failureDataSize = config.getInt("failureDataSize", DEFAULT_FAILURE_DATA_SIZE);

        initializeSubServices();
    }

    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance));
        pusSubServices.put(5, new SubServiceFive(yamcsInstance));
        pusSubServices.put(6, new SubServiceSix(yamcsInstance));
        pusSubServices.put(7, new SubServiceSeven(yamcsInstance));
        pusSubServices.put(8, new SubServiceEight(yamcsInstance));
        pusSubServices.put(10, new SubServiceTen(yamcsInstance));
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return pusSubServices.get(PusTmCcsdsPacket.getMessageSubType(tmPacket.getPacket())).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}
