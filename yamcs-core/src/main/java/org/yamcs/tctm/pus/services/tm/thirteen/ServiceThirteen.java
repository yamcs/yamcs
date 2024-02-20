package org.yamcs.tctm.pus.services.tm.thirteen;

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
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ServiceThirteen implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();

    String yamcsInstance;
    YConfiguration config;

    protected static Stream s13In;

    protected final int DEFAULT_LARGE_PACKET_TRANSACTION_ID_SIZE = 2;
    protected final int DEFAULT_PART_SEQUENCE_NUMBER = 2;
    protected final String DEFAULT_S13_IN_STREAM = "s13_in";
    protected final int DEFAULT_FAILURE_REASON_SIZE = 1;

    protected static int largePacketTransactionIdSize;
    protected static int partSequenceNumberSize;
    protected static String s13InStream;
    protected static int failureReasonSize;

    public ServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        largePacketTransactionIdSize = config.getInt("largePacketTransactionIdSize", DEFAULT_LARGE_PACKET_TRANSACTION_ID_SIZE);
        partSequenceNumberSize = config.getInt("partSequenceNumberSize", DEFAULT_PART_SEQUENCE_NUMBER);
        failureReasonSize = config.getInt("failureReasonSize", DEFAULT_FAILURE_REASON_SIZE);
        s13InStream = config.getString("s13InStream", DEFAULT_S13_IN_STREAM);

        initializeSubServices();

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        s13In = ydb.getStream(s13InStream);
    }

    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, config.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, config.getConfigOrEmpty("two")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, config.getConfigOrEmpty("three")));
        pusSubServices.put(16, new SubServiceSixteen(yamcsInstance, config.getConfigOrEmpty("sixteen")));
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        byte[] b = tmPacket.getPacket();
        return pusSubServices.get(PusTmCcsdsPacket.getMessageSubType(b)).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}
