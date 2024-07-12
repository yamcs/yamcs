package org.yamcs.tctm.pus.services.tm.five;

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
import org.yamcs.tctm.pus.tuples.Pair;
import org.yamcs.tctm.pus.tuples.Triple;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class ServiceFive implements PusService {
    Log log;

    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    YConfiguration serviceFiveConfig;
    private String yamcsInstance;

    protected static Map<Pair<Integer, Integer>, Pair<String, Map<Integer, Triple<Integer, String, Map<Integer, Pair<Integer, String>>>>>>eventIds = new HashMap<>();

    private final int DEFAULT_EVENTID_SIZE = 1;
    protected static int eventIdSize;

    public ServiceFive(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        serviceFiveConfig = config;

        // Load S5 mappings
        List<String> eventStr = config.getList("eventDefinitionId");
        YConfiguration eventIdConfig = YConfiguration.getConfiguration("five", "pus");
        
        for (String eventId: eventStr) {
            if (eventIdConfig.containsKey(eventId)) {
                YConfiguration eventIdMap = eventIdConfig.getConfig(eventId);

                Map<Integer, Triple<Integer, String, Map<Integer, Pair<Integer, String>>>> chunks = null;
                if (eventIdMap.containsKey("chunks")) {
                    chunks = new HashMap<>();

                    for (YConfiguration chunkConfig: eventIdMap.getConfigList("chunks")) {
                        int chunkValue = chunkConfig.getInt("value");
                        String chunkName = chunkConfig.getString("name");
                        int chunkLength = chunkConfig.getInt("length");

                        Map<Integer, Pair<Integer, String>> bits = null;
                        if (chunkConfig.containsKey("bits")) {
                            bits = new HashMap<>();

                            for (YConfiguration bitsConfig: chunkConfig.getConfigList("bits")) {
                                int bitValue = bitsConfig.getInt("value");
                                int bitLength = bitsConfig.getInt("length");
                                String bitName = bitsConfig.getString("name");

                                bits.put(bitValue,
                                    new Pair<>(bitLength, bitName)
                                );
                            }
                        }

                        chunks.put(chunkValue,
                            new Triple<>(chunkLength, chunkName, bits)
                        );                    
                    }
                }

                eventIds.put(new Pair<> (
                    eventIdMap.getInt("apid"), eventIdMap.getInt("value")
                ), new Pair<>(eventIdMap.getString("name"), chunks));
            }
        }        

        eventIdSize = config.getInt("eventIdSize", DEFAULT_EVENTID_SIZE);
        initializeSubServices();
    }

    public static long createOnes(int length) {
        byte[] bb = new byte[length];
        for (int i = 0; i < length; i++) {
            bb[i] = (byte) 0xFF; // Set each byte to 0xFF (11111111 in binary)
        }
        return ByteArrayUtils.decodeCustomInteger(bb, 0, length);
    }

    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("one")));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("two")));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("three")));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("four")));
        pusSubServices.put(8, new SubServiceEight(yamcsInstance, serviceFiveConfig.getConfigOrEmpty("eight")));
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
