package org.yamcs.tctm.pus.services.tc.six;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.CommandEncodingException;
import org.yamcs.tctm.pus.PusTcManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubServiceThree implements PusSubService {
    String yamcsInstance;
    YConfiguration config;

    SubServiceThree(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        PusTcCcsdsPacket.setPusHeadersSpareFieldAndSourceID(telecommand);

        byte[] binary = telecommand.getBinary();
        int apid = PusTcCcsdsPacket.getAPID(binary);
        byte[] dataField = PusTcCcsdsPacket.getDataField(binary);

        int memoryId = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceSix.memoryIdSize);
        int baseId = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceSix.memoryIdSize, ServiceSix.baseIdSize);
        int nFields = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceSix.memoryIdSize + ServiceSix.baseIdSize, ServiceSix.nfieldsSize);
        byte[] argOffsetValues = Arrays.copyOfRange(dataField, ServiceSix.memoryIdSize + ServiceSix.baseIdSize + ServiceSix.nfieldsSize, dataField.length);
        
        List<ServiceSix.Pair<Integer, Integer>> baseIdMap = ServiceSix.memoryIds.get(new ServiceSix.Pair<>(apid, memoryId)).get(baseId);
        ArrayList<byte[]> loadData = new ArrayList<>();

        while(nFields > 0) {
            int argOffsetValue = (int) ByteArrayUtils.decodeCustomInteger(argOffsetValues, 0, ServiceSix.offsetArgumentSize);
            
            for(ServiceSix.Pair<Integer, Integer> offsetMap: baseIdMap) {
                int offsetValue = offsetMap.getFirst();
                int dataLength = offsetMap.getSecond();

                if (argOffsetValue == offsetValue) {
                    ByteBuffer bb = ByteBuffer.wrap(new byte[ServiceSix.offsetSize + ServiceSix.lengthSize]);
                    bb.put(ByteArrayUtils.encodeCustomInteger(offsetValue, ServiceSix.offsetSize));
                    bb.put(ByteArrayUtils.encodeCustomInteger(dataLength, ServiceSix.lengthSize));

                    loadData.add(bb.array());
                    break;
                }
            }
            argOffsetValues = Arrays.copyOfRange(argOffsetValues, ServiceSix.offsetArgumentSize, argOffsetValues.length);
            nFields--;
        }

        // Construct new TC
        int loadDataSize = loadData.stream()
                .mapToInt(arr -> arr.length)
                .sum();
        ByteBuffer bb = ByteBuffer.wrap(new byte[PusTcManager.DEFAULT_PRIMARY_HEADER_LENGTH + PusTcManager.secondaryHeaderLength + ServiceSix.memoryIdSize + ServiceSix.baseIdSize + ServiceSix.nfieldsSize + loadDataSize]);
        bb.put(Arrays.copyOfRange(binary, 0, PusTcManager.DEFAULT_PRIMARY_HEADER_LENGTH + PusTcManager.secondaryHeaderLength + ServiceSix.memoryIdSize + ServiceSix.baseIdSize + ServiceSix.nfieldsSize));
        loadData.forEach(bb::put);

        telecommand.setBinary(bb.array());
        return telecommand;
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

}
