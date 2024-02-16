package org.yamcs.tctm.pus.services.tm.fourteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class SubServiceEight implements PusSubService {
    String yamcsInstance;
    YConfiguration config;

    public SubServiceEight(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        return null;
    }


    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int n1 = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceFourteen.n1Size);
        int n2 = 0;

        int applicationServiceId, housekeepingStructureId;
        ArrayList<byte[]> bytestream = new ArrayList<>();

        for(int i = 0; i < n1 ; i++) {
            int lengthI = ServiceFourteen.applicationIdSize + ServiceFourteen.n2Size + n2 * ServiceFourteen.housekeepingStructureIdSize;
            int indexI = i *  lengthI;

            applicationServiceId = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFourteen.n1Size + indexI, ServiceFourteen.applicationIdSize);
            n2 = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFourteen.n1Size + ServiceFourteen.applicationIdSize + indexI, ServiceFourteen.n2Size);

            for (int j = 0; j < n2; j++) {
                int indexJ = j * ServiceFourteen.housekeepingStructureIdSize;
                housekeepingStructureId = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFourteen.n1Size + ServiceFourteen.applicationIdSize + ServiceFourteen.n2Size + indexI + indexJ, ServiceFourteen.housekeepingStructureIdSize);

                // Add to bytestream | To be made into a new TmPacket
                ByteBuffer bb = ByteBuffer.wrap(new byte[ServiceFourteen.applicationIdSize + ServiceFourteen.housekeepingStructureIdSize]);

                bb.put(ByteArrayUtils.encodeCustomInteger((long) applicationServiceId, ServiceFourteen.applicationIdSize));
                bb.put(ByteArrayUtils.encodeCustomInteger((long) housekeepingStructureId, ServiceFourteen.housekeepingStructureIdSize));

                bytestream.add(bb.array());
            }
        }

        byte[] primaryHeader = pPkt.getPrimaryHeader();
        byte[] secondaryHeader = pPkt.getSecondaryHeader();
        ByteBuffer bb = ByteBuffer.wrap(new byte[primaryHeader.length + secondaryHeader.length + ServiceFourteen.numerationsCountSize + (ServiceFourteen.applicationIdSize + ServiceFourteen.housekeepingStructureIdSize) * bytestream.size()]);

        // Add a 2 byte field which carries the number of (Apid, housekeepingStructureId) numerations, which will be used in the MDb to extract the values
        bb.put(primaryHeader);
        bb.put(secondaryHeader);
        bb.putShort((short) bytestream.size());
        for(byte[] data: bytestream) {
            bb.put(data);
        }

        TmPacket newPkt = new TmPacket(tmPacket.getReceptionTime(), tmPacket.getGenerationTime(),
                tmPacket.getSeqCount(), bb.array());
        newPkt.setEarthReceptionTime(tmPacket.getEarthReceptionTime());

        ArrayList<TmPacket> pusPackets = new ArrayList<>();
        pusPackets.add(newPkt);

        return pusPackets;
    }
}
