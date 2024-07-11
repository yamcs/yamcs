package org.yamcs.tctm.pus.services.tm.eleven;

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


public class ServiceEleven implements PusService {
    Log log;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration config;
    private final String yamcsInstance;

    private static final int DEFAULT_REPORT_COUNT_SIZE = 1;
    private static final int DEFAULT_SOURCE_ID_SIZE = 2;
    private static final int DEFAULT_APID_SIZE = 2;
    private static final int DEFAULT_SEQ_COUNT_SIZE = 2;

    protected static int sourceIdSize;
    protected static int apidSize;
    protected static int seqCountSize;
    protected static int reportCountSize;

    public ServiceEleven(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        reportCountSize = config.getInt("reportCountSize", DEFAULT_REPORT_COUNT_SIZE);
        sourceIdSize = config.getInt("sourceIdSize", DEFAULT_SOURCE_ID_SIZE);
        apidSize = config.getInt("apidSize", DEFAULT_APID_SIZE);
        seqCountSize = config.getInt("seqCountSize", DEFAULT_SEQ_COUNT_SIZE);

        initializeSubServices();
    }

    public void initializeSubServices() {
        pusSubServices.put(10, new SubServiceTen(yamcsInstance, config.getConfigOrEmpty("ten")));
        pusSubServices.put(13, new SubServiceThirteen(yamcsInstance, config.getConfigOrEmpty("thirteen")));
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
