package org.yamcs.tctm.pus.services.tc.thirteen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;

public class ServiceThirteen implements PusService {
    Log log;
    private String yamcsInstance;

    protected final int DEFAULT_LARGE_PACKET_TRANSACTION_ID_SIZE = 2;
    protected final int DEFAULT_PART_SEQUENCE_NUMBER = 2;

    public static int largePacketTransactionIdSize;
    public static int partSequenceNumberSize;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration config;

    public ServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        largePacketTransactionIdSize = config.getInt("largePacketTransactionIdSize", DEFAULT_LARGE_PACKET_TRANSACTION_ID_SIZE);
        partSequenceNumberSize = config.getInt("partSequenceNumberSize", DEFAULT_PART_SEQUENCE_NUMBER);

        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(9, new SubServiceNine(yamcsInstance, config.getConfigOrEmpty("nine")));
        pusSubServices.put(10, new SubServiceTen(yamcsInstance, config.getConfigOrEmpty("ten")));
        pusSubServices.put(11, new SubServiceEleven(yamcsInstance, config.getConfigOrEmpty("eleven")));
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return pusSubServices.get(PusTcCcsdsPacket.getMessageSubType(telecommand)).process(telecommand);
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractPusModifiers'");
    }
}
