package org.yamcs.tctm.pus.services.tm.fifteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.tctm.pus.services.tm.fifteen.ServiceFifteen;
import org.yamcs.utils.ByteArrayUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class SubServiceThirtySix implements PusSubService {
    String yamcsInstance;
    YConfiguration config;


    public SubServiceThirtySix(String yamcsInstance, YConfiguration config) {
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

        int packetStoreId = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceFifteen.packetStoreIdSize);
        int n1 = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize, ServiceFifteen.n1Size);
        int n2 = 0;

        int applicationServiceId, housekeepingStructureId;
        ArrayList<byte[]> bytestream = new ArrayList<>();

        for(int i = 0; i < n1 ; i++) {
            int lengthI = ServiceFifteen.applicationIdSize + ServiceFifteen.n2Size + n2 * ServiceFifteen.housekeepingStructureIdSize;
            int indexI = i *  lengthI;

            applicationServiceId = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize + ServiceFifteen.n1Size + indexI, ServiceFifteen.applicationIdSize);
            n2 = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize + ServiceFifteen.n1Size + ServiceFifteen.applicationIdSize + indexI, ServiceFifteen.n2Size);

            for (int j = 0; j < n2; j++) {
                int indexJ = j * ServiceFifteen.housekeepingStructureIdSize;
                housekeepingStructureId = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize + ServiceFifteen.n1Size + ServiceFifteen.applicationIdSize + ServiceFifteen.n2Size + indexI + indexJ, ServiceFifteen.housekeepingStructureIdSize);

                // Add to bytestream | To be made into a new TmPacket
                ByteBuffer bb = ByteBuffer.wrap(new byte[ServiceFifteen.packetStoreIdSize + ServiceFifteen.applicationIdSize + ServiceFifteen.housekeepingStructureIdSize]);

                bb.put(ByteArrayUtils.encodeCustomInteger((long) packetStoreId, ServiceFifteen.packetStoreIdSize));
                bb.put(ByteArrayUtils.encodeCustomInteger((long) applicationServiceId, ServiceFifteen.applicationIdSize));
                bb.put(ByteArrayUtils.encodeCustomInteger((long) housekeepingStructureId, ServiceFifteen.housekeepingStructureIdSize));

                bytestream.add(bb.array());
            }
        }

        byte[] primaryHeader = pPkt.getPrimaryHeader();
        byte[] secondaryHeader = pPkt.getSecondaryHeader();
        ByteBuffer bb = ByteBuffer.wrap(new byte[primaryHeader.length + secondaryHeader.length + ServiceFifteen.numerationsCountSize + (ServiceFifteen.packetStoreIdSize + ServiceFifteen.applicationIdSize + ServiceFifteen.housekeepingStructureIdSize) * bytestream.size()]);

        // Add a 2 byte field which carries the number of (packetStoreId, Apid, housekeepingStructureId) numerations, which will be used in the MDb to extract the values
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
