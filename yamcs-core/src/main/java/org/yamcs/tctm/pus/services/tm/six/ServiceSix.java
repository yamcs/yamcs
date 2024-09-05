package org.yamcs.tctm.pus.services.tm.six;

import java.util.*;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.tuples.Pair;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class ServiceSix implements PusService {
    Log log;
    Map<Integer, PusSubService> subServices = new HashMap<>();
    String yamcsInstance;
    YConfiguration config;

    protected static Map<Pair<Integer, Integer>, Map<Integer, Map<Integer, Integer>>> memoryIds = new HashMap<>();

    protected static int DEFAULT_MEMORY_ID_SIZE = 1;
    protected static int DEFAULT_BASE_ID_SIZE = 1;
    protected static int DEFAULT_NFIELDS_SIZE = 1;
    protected static int DEFAULT_OFFSET_SIZE = 2;
    protected static int DEFAULT_LENGTH_SIZE = 1;

    protected static int memoryIdSize;
    protected static int baseIdSize;
    protected static int nfieldsSize;
    protected static int offsetSize;
    protected static int lengthSize;

    protected static int checksumSize = 2;
    protected static CrcCciitCalculator crc = new CrcCciitCalculator();

    public ServiceSix(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        memoryIdSize = config.getInt("memoryIdSize", DEFAULT_MEMORY_ID_SIZE);
        baseIdSize = config.getInt("baseIdSize", DEFAULT_BASE_ID_SIZE);
        nfieldsSize = config.getInt("nfieldsSize", DEFAULT_NFIELDS_SIZE);
        offsetSize = config.getInt("offsetSize", DEFAULT_OFFSET_SIZE);
        lengthSize = config.getInt("lengthSize", DEFAULT_LENGTH_SIZE);

        // Load Group<>Offset mapping
        List<String> memoryIdStr = config.getList("memoryId");
        YConfiguration memoryIdConfig = YConfiguration.getConfiguration("six", "pus");
        
        for (String memoryId: memoryIdStr) {
            if (memoryIdConfig.containsKey(memoryId)) {
                YConfiguration memoryIdMap = memoryIdConfig.getConfig(memoryId);

                Map<Integer, Map<Integer, Integer>> baseIds = new HashMap<>();
                for (YConfiguration baseConfig: memoryIdMap.getConfigList("baseId")) {
                    int baseIdValue = baseConfig.getInt("value");

                    Map<Integer, Integer> offsets = new HashMap<>();
                    for (YConfiguration offsetConfig: baseConfig.getConfigList("offsets")) {
                        offsets.put(offsetConfig.getInt("value"), offsetConfig.getInt("group"));
                    }
                    baseIds.put(baseIdValue, offsets);
                }

                memoryIds.put(new Pair<> (
                    memoryIdMap.getInt("apid"), memoryIdMap.getInt("value")
                ), baseIds);
            }
        }

        initializeSubServices();
    }

    public void initializeSubServices() {
        subServices.put(4, new SubServiceFour(yamcsInstance, config.getConfigOrEmpty("four")));
        subServices.put(6, new SubServiceSix(yamcsInstance, config.getConfigOrEmpty("six")));
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return subServices.get(PusTmCcsdsPacket.getMessageSubType(tmPacket.getPacket())).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }

    public static HashMap<Integer, byte[]> keySort(HashMap<Integer, byte[]> map) {
        List<Map.Entry<Integer, byte[]>> entryList = new ArrayList<>(map.entrySet());
        entryList.sort(Map.Entry.comparingByKey());

        LinkedHashMap<Integer, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : entryList) {
            result.put(entry.getKey(), entry.getValue());
        }
        
        return result;
    }
}