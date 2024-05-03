package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.tctm.ccsds.Cop1TcPacketHandler;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13OutgoingTransfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;

public class StartS13DownlinkPacket extends UplinkS13Packet {

    public StartS13DownlinkPacket(S13TransactionId transactionId, String fullyQualifiedCmdName) {
        super(transactionId, fullyQualifiedCmdName, false);
    }

    @Override
    public PreparedCommand generatePreparedCommand(OngoingS13Transfer trans) throws BadRequestException, InternalServerErrorException {
        Map<String, Object> assignments = new LinkedHashMap<>();
        assignments.put("Pus_Acknowledgement_Flags", "Acceptance | Completion");
        assignments.put("Parameter_ID", uniquenessId.getLargePacketTransactionId());

        PreparedCommand pc = trans.createS13Telecommand(fullyQualifiedCmdName, assignments, trans.getCommandReleaseUser());

        // Set extra options
        pc.setAttribute(Cop1TcPacketHandler.OPTION_BYPASS.getId(), S13OutgoingTransfer.cop1Bypass);

        return pc;
    }
}
