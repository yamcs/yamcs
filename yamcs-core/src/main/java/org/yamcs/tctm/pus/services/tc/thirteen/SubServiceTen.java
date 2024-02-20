package org.yamcs.tctm.pus.services.tc.thirteen;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.PusTcManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;

public class SubServiceTen implements PusSubService {
    String yamcsInstance;

    SubServiceTen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        PreparedCommand pc = PusTcCcsdsPacket.setPusHeadersSpareFieldAndSourceID(telecommand);
        
        byte[] binary = pc.getBinary();
        byte[] dataField = PusTcCcsdsPacket.getDataField(binary);

        // Get File Part with Padding
        byte[] filePartSizeWithPadding = Arrays.copyOfRange(dataField, 
                ServiceThirteen.largePacketTransactionIdSize + ServiceThirteen.partSequenceNumberSize, dataField.length);

        int filePartActualSize;
        if (pc.hasAttribute("FilePartSize")) {
            // Get Actual File Part from CmdHistoryAttribute
            filePartActualSize = pc.getSignedIntegerAttribute("FilePartSize");

        } else {
            filePartActualSize = filePartSizeWithPadding.length;
        }

        // Extract just the actual File Part from the padded binary
        byte[] filePartActual = Arrays.copyOfRange(filePartSizeWithPadding, 0, filePartActualSize);

        // Obtain the new length of the packet
        int newPacketLength = PusTcManager.DEFAULT_PRIMARY_HEADER_LENGTH + PusTcManager.secondaryHeaderLength + ServiceThirteen.largePacketTransactionIdSize + ServiceThirteen.partSequenceNumberSize + filePartActualSize;

        // Create new packet
        ByteBuffer bb = ByteBuffer.wrap(new byte[newPacketLength]);
        bb.put(Arrays.copyOfRange(binary, 0, PusTcManager.DEFAULT_PRIMARY_HEADER_LENGTH + PusTcManager.secondaryHeaderLength + ServiceThirteen.largePacketTransactionIdSize
                + ServiceThirteen.partSequenceNumberSize)); // Primary + Secondary + LargeId + PartSeqNumber
        bb.put(filePartActual);

        // Set Binary
        pc.setBinary(bb.array());
        return pc;
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

}
