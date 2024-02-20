package org.yamcs.tctm.pus.services.tm.fifteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class SubServiceSix implements PusSubService {
    String yamcsInstance;
    YConfiguration config;


    public SubServiceSix(String yamcsInstance, YConfiguration config) {
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
        int n2 = 0, n3 = 0;

        int applicationServiceId, serviceType, subServiceType;
        ArrayList<byte[]> bytestream = new ArrayList<>();

        for(int i = 0; i < n1 ; i++) {
            int lengthI = (ServiceFifteen.applicationIdSize + ServiceFifteen.n2Size + n2 * (ServiceFifteen.serviceTypeSize + ServiceFifteen.n3Size + (n3 * ServiceFifteen.subServiceTypeSize)));
            int indexI = i *  lengthI;

            applicationServiceId = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize + ServiceFifteen.n1Size + indexI, ServiceFifteen.applicationIdSize);
            n2 = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize + ServiceFifteen.n1Size + ServiceFifteen.applicationIdSize + indexI, ServiceFifteen.n2Size);

            for (int j = 0; j < n2; j++) {
                int lengthJ = ServiceFifteen.serviceTypeSize + ServiceFifteen.n3Size + (n3 * ServiceFifteen.subServiceTypeSize);
                int indexJ = j * lengthJ;

                serviceType = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize + ServiceFifteen.n1Size + ServiceFifteen.applicationIdSize + ServiceFifteen.n2Size + indexI + indexJ, ServiceFifteen.serviceTypeSize);
                n3 = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize + ServiceFifteen.n1Size + ServiceFifteen.applicationIdSize + ServiceFifteen.n2Size + ServiceFifteen.serviceTypeSize + indexI + indexJ, ServiceFifteen.n3Size);

                for(int k = 0; k < n3 ; k++) {
                    int lengthK = ServiceFifteen.subServiceTypeSize;
                    int indexK = k * lengthK;

                    subServiceType = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFifteen.packetStoreIdSize + ServiceFifteen.n1Size + ServiceFifteen.applicationIdSize + ServiceFifteen.n2Size + ServiceFifteen.serviceTypeSize + ServiceFifteen.n3Size + indexI + indexJ + indexK, ServiceFifteen.subServiceTypeSize);

                    // Add to bytestream | To be made into a new TmPacket
                    ByteBuffer bb = ByteBuffer.wrap(new byte[ServiceFifteen.packetStoreIdSize + ServiceFifteen.applicationIdSize + ServiceFifteen.serviceTypeSize + ServiceFifteen.subServiceTypeSize]);

                    bb.put(ByteArrayUtils.encodeCustomInteger((long) packetStoreId, ServiceFifteen.packetStoreIdSize));
                    bb.put(ByteArrayUtils.encodeCustomInteger((long) applicationServiceId, ServiceFifteen.applicationIdSize));
                    bb.put(ByteArrayUtils.encodeCustomInteger((long) serviceType, ServiceFifteen.serviceTypeSize));
                    bb.put(ByteArrayUtils.encodeCustomInteger((long) subServiceType, ServiceFifteen.subServiceTypeSize));

                    bytestream.add(bb.array());
                }
            }
        }

        byte[] primaryHeader = pPkt.getPrimaryHeader();
        byte[] secondaryHeader = pPkt.getSecondaryHeader();

        ByteBuffer bb = ByteBuffer.wrap(new byte[primaryHeader.length + secondaryHeader.length + ServiceFifteen.numerationsCountSize + (ServiceFifteen.packetStoreIdSize + ServiceFifteen.applicationIdSize + ServiceFifteen.serviceTypeSize + ServiceFifteen.subServiceTypeSize) * bytestream.size()]);
        // Add a 2 byte field which carries the number of (packetStoreId, Apid, serviceType, subServiceType) numerations, which will be used in the MDb to extract the values
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
