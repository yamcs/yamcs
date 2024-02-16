package org.yamcs.tctm.pus.services.tm.fourteen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class ServiceFourteen implements PusService {
    private final String yamcsInstance;

    Map<Integer, PusSubService> subServices = new HashMap<>();
    YConfiguration config;

    protected static int DEFAULT_APPLICATION_PROCESS_ID_SIZE = 2;
    protected static int DEFAULT_SERVICE_TYPE_SIZE = 2;
    protected static int DEFAULT_SUBSERVICE_TYPE_SIZE = 2;
    protected static int DEFAULT_HOUSEKEEPING_STRUCTURE_ID_SIZE = 1;
    protected static int DEFAULT_DIAGNOSTIC_STRUCTURE_ID_SIZE = 1;
    protected static int DEFAULT_SUBSAMPLING_RATE_SIZE = 2;
    protected static int DEFAULT_EVENT_DEFINITION_ID = 2;

    protected static int DEFAULT_N1_SIZE = 2;
    protected static int DEFAULT_N2_SIZE = 2;
    protected static int DEFAULT_N3_SIZE = 2;

    protected static int applicationIdSize;
    protected static int serviceTypeSize;
    protected static int subServiceTypeSize;
    protected static int housekeepingStructureIdSize;
    protected static int diagnosticStructureIdSize;
    protected static int subSamplingRateSize;
    protected static int eventDefinitionIdSize;
    protected static int n1Size;
    protected static int n2Size;
    protected static int n3Size;
    protected static int numerationsCountSize = 2;

    public ServiceFourteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        initializeSubServices();
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
    }

    @Override
    public void initializeSubServices() {
        subServices.put(4, new SubServiceFour(yamcsInstance,
                config.getConfigOrEmpty("four")));
        subServices.put(8, new SubServiceEight(yamcsInstance,
                config.getConfigOrEmpty("eight")));
        subServices.put(12, new SubServiceTwelve(yamcsInstance,
                config.getConfigOrEmpty("twelve")));
        subServices.put(16, new SubServiceSixteen(yamcsInstance,
                config.getConfigOrEmpty("sixteen")));
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return subServices.get(PusTmCcsdsPacket.getMessageSubType(tmPacket.getPacket())).process(tmPacket);
    }
}
