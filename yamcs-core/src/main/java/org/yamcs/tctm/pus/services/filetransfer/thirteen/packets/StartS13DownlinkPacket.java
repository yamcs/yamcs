package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;
import org.yamcs.yarch.Tuple;

public class StartS13DownlinkPacket extends FileTransferPacket {
    protected String fullyQualifiedCmdName;

    public StartS13DownlinkPacket(S13TransactionId transactionId, String fullyQualifiedCmdName) {
        super(transactionId);
        this.fullyQualifiedCmdName = fullyQualifiedCmdName;
    }

    public Tuple toTuple(OngoingS13Transfer trans) {
        Map<String, Object> assignments = new LinkedHashMap<>();

        assignments.put("Pus_Acknowledgement_Flags", "Acceptance | Completion");
        assignments.put("Large_Message_Trasaction_Identifier", transactionId.getLargePacketTransactionId());

        PreparedCommand pc = trans.createS13Telecommand(fullyQualifiedCmdName, assignments, null);  // FIXME: Include some user | No clue how to get
        return pc.toTuple();
    }
    
}
