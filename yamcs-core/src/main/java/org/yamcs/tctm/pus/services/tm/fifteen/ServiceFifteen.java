package org.yamcs.tctm.pus.services.tm.fifteen;

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

    protected static int DEFAULT_PACKET_STORE_ID_SIZE = 2;
    protected static int DEFAULT_N1_SIZE = 2;
    protected static int DEFAULT_N2_SIZE = 2;
    protected static int DEFAULT_N3_SIZE = 2;
    protected static int DEFAULT_APPLICATION_PROCESS_ID_SIZE = 2;
    protected static int DEFAULT_SERVICE_TYPE_SIZE = 2;
    protected static int DEFAULT_SUBSERVICE_TYPE_SIZE = 2;
    protected static int DEFAULT_HOUSEKEEPING_STRUCTURE_ID_SIZE = 1;
    protected static int DEFAULT_DIAGNOSTIC_STRUCTURE_ID_SIZE = 1;
    protected static int DEFAULT_SUBSAMPLING_RATE_SIZE = 2;
    protected static int DEFAULT_EVENT_DEFINITION_ID = 2;

    protected static int applicationIdSize;
    protected static int serviceTypeSize;
    protected static int subServiceTypeSize;
    protected static int housekeepingStructureIdSize;
    protected static int diagnosticStructureIdSize;
    protected static int subSamplingRateSize;
    protected static int eventDefinitionIdSize;
    protected static int packetStoreIdSize;
    protected static int n1Size;
    protected static int n2Size;
    protected static int n3Size;
    protected static int numerationsCountSize = 2;

    public ServiceFifteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceFiveConfig = config;

        packetStoreIdSize = config.getInt("packetStoreIdSize", DEFAULT_PACKET_STORE_ID_SIZE);

        applicationIdSize = config.getInt("applicationIdSize", DEFAULT_APPLICATION_PROCESS_ID_SIZE);
        serviceTypeSize = config.getInt("serviceTypeSize", DEFAULT_SERVICE_TYPE_SIZE);
        subServiceTypeSize = config.getInt("subServiceTypeSize", DEFAULT_SUBSERVICE_TYPE_SIZE);

        housekeepingStructureIdSize = config.getInt("housekeepingStructureIdSize", DEFAULT_HOUSEKEEPING_STRUCTURE_ID_SIZE);
        diagnosticStructureIdSize = config.getInt("diagnosticStructureIdSize", DEFAULT_DIAGNOSTIC_STRUCTURE_ID_SIZE);
        subSamplingRateSize = config.getInt("subSamplingRateSize", DEFAULT_SUBSAMPLING_RATE_SIZE);
        eventDefinitionIdSize = config.getInt("eventDefinitionIdSize", DEFAULT_EVENT_DEFINITION_ID);

        n1Size = config.getInt("n1Size", DEFAULT_N1_SIZE);
        n2Size = config.getInt("n2Size", DEFAULT_N2_SIZE);
        n3Size = config.getInt("n3Size", DEFAULT_N3_SIZE);

        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(6, new SubServiceSix(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("six")));
        pusSubServices.put(13, new SubServiceThirteen(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirteen")));
        pusSubServices.put(19, new SubServiceNineteen(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("nineteen")));
        pusSubServices.put(23, new SubServiceTwentyThree(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("twentyThree")));
        pusSubServices.put(36, new SubServiceThirtySix(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtySix")));
        pusSubServices.put(38, new SubServiceThirtyEight(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("thirtyEight")));
        pusSubServices.put(40, new SubServiceForty(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("forty")));
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
