package org.yamcs.tctm.pus.services.tm.six;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.tctm.pus.tuples.Pair;

import java.nio.ByteBuffer;
import java.util.*;

public class SubServiceFour implements PusSubService {
    String yamcsInstance;
    YConfiguration config;
    int defaultGroupIdSize = 1;

    SubServiceFour(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int apid = pPkt.getAPID();
        int memoryId = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceSix.memoryIdSize);
        int baseId = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceSix.memoryIdSize, ServiceSix.baseIdSize);
        int nFields = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceSix.memoryIdSize + ServiceSix.baseIdSize, ServiceSix.nfieldsSize);
        int nFieldsToBeFilled = nFields;

        byte[] dumpData = Arrays.copyOfRange(dataField, ServiceSix.memoryIdSize + ServiceSix.baseIdSize + ServiceSix.nfieldsSize, dataField.length);

        HashMap<Integer, byte[]> dumpObject = new HashMap<>();
        int totalLength = 0;

        Integer groupId = null;
        Map<Integer, Integer> offsetGroupMap = null;
        Map<Integer, Map<Integer, Integer>> baseIdMap = ServiceSix.memoryIds.get(new Pair<>(apid, memoryId));
        if (baseIdMap != null)
            offsetGroupMap = baseIdMap.get(baseId);

        while (nFields > 0) {
            int offset = (int) ByteArrayUtils.decodeCustomInteger(dumpData, 0, ServiceSix.offsetSize);
            int length = (int) ByteArrayUtils.decodeCustomInteger(dumpData, ServiceSix.offsetSize, ServiceSix.lengthSize);

            if (offsetGroupMap != null)
                groupId = offsetGroupMap.get(offset);

            int checksum = (int) ByteArrayUtils.decodeCustomInteger(dumpData, ServiceSix.offsetSize + ServiceSix.lengthSize + length, ServiceSix.checksumSize);
            /*
             * FIXME: Verify the checksum | But how?
             *  In case of checksum verification failure for an offset, the MDb unfortunately still expects all the N fields to be filled, in the correct order
             * */
            dumpObject.put(offset, Arrays.copyOfRange(dumpData, ServiceSix.offsetSize + ServiceSix.lengthSize, ServiceSix.offsetSize + ServiceSix.lengthSize + length));
            totalLength += length;

            // Reset the dumpData for the next iteration
            dumpData = Arrays.copyOfRange(dumpData, ServiceSix.offsetSize + ServiceSix.lengthSize + length + ServiceSix.checksumSize, dumpData.length);
            nFields--;
        }

        // Sort dumpedObject based on offset value
        HashMap<Integer, byte[]> sortedDumpObject = ServiceSix.keySort(dumpObject);

        // Construct new bytearray payload
        byte[] primaryHeader = pPkt.getPrimaryHeader();
        byte[] secondaryHeader = pPkt.getSecondaryHeader();

        ByteBuffer bb;
        if (groupId != null)
            bb = ByteBuffer.wrap(new byte[primaryHeader.length + secondaryHeader.length + ServiceSix.memoryIdSize + ServiceSix.baseIdSize + ServiceSix.nfieldsSize + totalLength + defaultGroupIdSize]);
        else
            bb = ByteBuffer.wrap(new byte[primaryHeader.length + secondaryHeader.length + ServiceSix.memoryIdSize + ServiceSix.baseIdSize + ServiceSix.nfieldsSize + totalLength]);

        bb.put(primaryHeader);
        bb.put(secondaryHeader);
        bb.put(ByteArrayUtils.encodeCustomInteger(memoryId, ServiceSix.memoryIdSize));
        bb.put(ByteArrayUtils.encodeCustomInteger(baseId, ServiceSix.baseIdSize));
        bb.put(ByteArrayUtils.encodeCustomInteger(nFieldsToBeFilled, ServiceSix.nfieldsSize));
        if (groupId != null)
            bb.put(ByteArrayUtils.encodeCustomInteger(groupId.intValue(), defaultGroupIdSize));
        sortedDumpObject.forEach((key, value) -> bb.put(value));

        // Construct new TmPacket
        TmPacket newPkt = new TmPacket(tmPacket.getReceptionTime(), tmPacket.getGenerationTime(),
                tmPacket.getSeqCount(), bb.array());
        newPkt.setEarthReceptionTime(tmPacket.getEarthReceptionTime());

        ArrayList<TmPacket> pusPackets = new ArrayList<>();
        pusPackets.add(newPkt);
        
        return pusPackets;
    }
}