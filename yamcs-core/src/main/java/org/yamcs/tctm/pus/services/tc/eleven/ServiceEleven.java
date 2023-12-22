package org.yamcs.tctm.pus.services.tc.eleven;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcModifier;

public class ServiceEleven implements PusService{
    Log log;
    private String yamcsInstance;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceElevenConfig;

    final static int DEFAULT_FSW_APID = 20;     // FIXME: Check if correct

    // Primary Header fields
    final static byte ccsdsVersionNumber = 0;   // FIXME: Check if correct
    final static byte ccsdsPacketType = 1;
    final static byte secondaryHeaderFlag = 1;
    final static byte sequenceFlags = 3;
    static short fswApid;
    final static short packetSequenceCount = 0; // `CcsdsSeqCountFiller` class will fill up the seqCount when invoked within the PostProcessor

    // Secondary Header fields
    final static byte pusVersionNumber = 2;
    final static byte acknowledgementFlags = 15; // FIME: Confirm once
    final static byte serviceType = 11;


    public ServiceEleven(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceElevenConfig = config;

        fswApid = (short) config.getInt("fswApid", ServiceEleven.DEFAULT_FSW_APID);
        initializeSubServices();
    }

    @Override
    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("two")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("three")));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("four")));
        pusSubServices.put(5, new SubServiceFive(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("five")));
        pusSubServices.put(6, new SubServiceSix(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("six")));
        pusSubServices.put(16, new SubServiceSixteen(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("sixteen")));
        pusSubServices.put(17, new SubServiceSeventeen(yamcsInstance, serviceElevenConfig.getConfigOrEmpty("seventeen")));
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        return pusSubServices.get(PusTcModifier.getMessageSubType(telecommand)).process(telecommand);
    }

    @Override
    public TmPacket extractPusModifiers(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractPusModifiers'");
    }
    
    public PreparedCommand addTimetagModifiers(PreparedCommand telecommand) {
        return pusSubServices.get(4).process(telecommand);
    }
}
