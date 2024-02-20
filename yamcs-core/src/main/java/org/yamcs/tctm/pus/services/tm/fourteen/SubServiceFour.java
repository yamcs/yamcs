package org.yamcs.tctm.pus.services.tm.fourteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class SubServiceFour implements PusSubService {
    String yamcsInstance;
    YConfiguration config;

    public SubServiceFour(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        return null;
    }

    public TmPacket createTmPacket(TmPacket tmPacket, int applicationProcessId, int serviceType, int subServiceType) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] primaryHeader = pPkt.getPrimaryHeader();
        byte[] secondaryHeader = pPkt.getSecondaryHeader();

        byte[] newBinary = new byte[primaryHeader.length + secondaryHeader.length + ServiceFourteen.applicationIdSize + ServiceFourteen.serviceTypeSize + ServiceFourteen.subServiceTypeSize];
        ByteBuffer bb = ByteBuffer.wrap(newBinary);

        bb.put(primaryHeader);
        bb.put(secondaryHeader);
        bb.putShort((short) applicationProcessId);
        bb.putShort((short) serviceType);
        bb.putShort((short) subServiceType);

        TmPacket newPkt = new TmPacket(tmPacket.getReceptionTime(), tmPacket.getGenerationTime(),
                tmPacket.getSeqCount(), bb.array());
        newPkt.setEarthReceptionTime(tmPacket.getEarthReceptionTime());

        return newPkt;
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int n1 = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceFourteen.n1Size);
        int n2 = 0, n3 = 0;

        int applicationServiceId, serviceType, subServiceType;
        ArrayList<byte[]> bytestream = new ArrayList<>();

        for(int i = 0; i < n1 ; i++) {
            int lengthI = (ServiceFourteen.applicationIdSize + ServiceFourteen.n2Size + n2 * (ServiceFourteen.serviceTypeSize + ServiceFourteen.n3Size + (n3 * ServiceFourteen.subServiceTypeSize)));
            int indexI = i *  lengthI;

            applicationServiceId = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFourteen.n1Size + indexI, ServiceFourteen.applicationIdSize);
            n2 = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFourteen.n1Size + ServiceFourteen.applicationIdSize + indexI, ServiceFourteen.n2Size);

            for (int j = 0; j < n2; j++) {
                int lengthJ = ServiceFourteen.serviceTypeSize + ServiceFourteen.n3Size + (n3 * ServiceFourteen.subServiceTypeSize);
                int indexJ = j * lengthJ;

                serviceType = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFourteen.n1Size + ServiceFourteen.applicationIdSize + ServiceFourteen.n2Size + indexI + indexJ, ServiceFourteen.serviceTypeSize);
                n3 = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFourteen.n1Size + ServiceFourteen.applicationIdSize + ServiceFourteen.n2Size + ServiceFourteen.serviceTypeSize + indexI + indexJ, ServiceFourteen.n3Size);

                for(int k = 0; k < n3 ; k++) {
                    int lengthK = ServiceFourteen.subServiceTypeSize;
                    int indexK = k * lengthK;

                    subServiceType = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceFourteen.n1Size + ServiceFourteen.applicationIdSize + ServiceFourteen.n2Size + ServiceFourteen.serviceTypeSize + ServiceFourteen.n3Size + indexI + indexJ + indexK, ServiceFourteen.subServiceTypeSize);

                    // Add to bytestream | To be made into a new TmPacket
                    ByteBuffer bb = ByteBuffer.wrap(new byte[ServiceFourteen.applicationIdSize + ServiceFourteen.serviceTypeSize + ServiceFourteen.subServiceTypeSize]);

                    bb.put(ByteArrayUtils.encodeCustomInteger((long) applicationServiceId, ServiceFourteen.applicationIdSize));
                    bb.put(ByteArrayUtils.encodeCustomInteger((long) serviceType, ServiceFourteen.serviceTypeSize));
                    bb.put(ByteArrayUtils.encodeCustomInteger((long) subServiceType, ServiceFourteen.subServiceTypeSize));

                    bytestream.add(bb.array());
                }
            }
        }

        byte[] primaryHeader = pPkt.getPrimaryHeader();
        byte[] secondaryHeader = pPkt.getSecondaryHeader();

        ByteBuffer bb = ByteBuffer.wrap(new byte[primaryHeader.length + secondaryHeader.length + ServiceFourteen.numerationsCountSize + (ServiceFourteen.applicationIdSize + ServiceFourteen.serviceTypeSize + ServiceFourteen.subServiceTypeSize) * bytestream.size()]);
        // Add a 2 byte field which carries the number of (Apid, serviceType, subServiceType) nnumerations, which will be used in the MDb to extract the values
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
