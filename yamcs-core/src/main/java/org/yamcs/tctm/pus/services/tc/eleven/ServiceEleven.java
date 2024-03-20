package org.yamcs.tctm.pus.services.tc.eleven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;


public class ServiceEleven implements PusService {
    Log log;
    private final String yamcsInstance;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration config;

    // Primary Header fields
    final static byte ccsdsVersionNumber = 0;   // FIXME: Check if correct
    final static byte ccsdsPacketType = 1;
    final static byte secondaryHeaderFlag = 1;
    final static byte sequenceFlags = 3;
    final static short packetSequenceCount = 0; // `CcsdsSeqCountFiller` class will fill up the seqCount when invoked within the PostProcessor

    // Secondary Header fields
    final static byte pusVersionNumber = 32; // After bit-manipulation, it will be correctly inserted in the TC
    final static byte serviceType = 11;

    // APID mapping
    static Map<Integer, Short> fswApidMap = new HashMap<>();

    public ServiceEleven(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        for(YConfiguration c: config.getConfigList("fswApidMap")) {
            List<Integer> inApid = c.getList("inApid");
            int outApid = c.getInt("outApid");

            for (int apid: inApid) {
                fswApidMap.put(apid, (short) outApid);
            }
        }
        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, config.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, config.getConfigOrEmpty("two")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, config.getConfigOrEmpty("three")));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance, config.getConfigOrEmpty("four")));
        pusSubServices.put(5, new SubServiceFive(yamcsInstance, config.getConfigOrEmpty("five")));
        pusSubServices.put(6, new SubServiceSix(yamcsInstance, config.getConfigOrEmpty("six")));
        pusSubServices.put(9, new SubServiceNine(yamcsInstance, config.getConfigOrEmpty("nine")));
        pusSubServices.put(11, new SubServiceEleven(yamcsInstance, config.getConfigOrEmpty("eleven")));
        pusSubServices.put(12, new SubServiceTwelve(yamcsInstance, config.getConfigOrEmpty("twelve")));
        pusSubServices.put(14, new SubServiceFourteen(yamcsInstance, config.getConfigOrEmpty("fourteen")));
        pusSubServices.put(16, new SubServiceSixteen(yamcsInstance, config.getConfigOrEmpty("sixteen")));
        pusSubServices.put(17, new SubServiceSeventeen(yamcsInstance, config.getConfigOrEmpty("seventeen")));
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
    
    public PreparedCommand addTimetagModifiers(PreparedCommand telecommand) {
        return pusSubServices.get(4).process(telecommand);
    }
}
