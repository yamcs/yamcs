package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.tctm.ccsds.Cop1TcPacketHandler;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13OutgoingTransfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;
import org.yamcs.yarch.Tuple;


public class UplinkS13Packet extends FileTransferPacket {
    protected String fullyQualifiedCmdName;
    protected long partSequenceNumber;
    protected byte[] filePart;

    public UplinkS13Packet(S13TransactionId transactionId, long partSequenceNumber, String fullyQualifiedCmdName, byte[] filePart) {
        super(transactionId);

        this.fullyQualifiedCmdName = fullyQualifiedCmdName;
        this.partSequenceNumber = partSequenceNumber;
        this.filePart = filePart;
    }

    public PreparedCommand getPreparedCommand(OngoingS13Transfer trans) {
        Map<String, Object> assignments = new LinkedHashMap<>();

        assignments.put("Pus_Acknowledgement_Flags", "Acceptance | Completion");
        assignments.put("Large_Message_Trasaction_Identifier", transactionId.getLargePacketTransactionId());
        assignments.put("Part_Sequence_Number", partSequenceNumber);
        assignments.put("File_Part", filePart);

        PreparedCommand pc = trans.createS13Telecommand(fullyQualifiedCmdName, assignments, trans.getCommandReleaseUser());
        
        // Set extra options
        var commandOption = YamcsServer.getServer().getCommandOption(Cop1TcPacketHandler.OPTION_BYPASS.getId());
        Value v = Value.newBuilder()
                    .setType(Value.Type.BOOLEAN)
                    .setBooleanValue(S13OutgoingTransfer.cop1Bypass)
                    .build();
        pc.addAttribute(CommandHistoryAttribute.newBuilder()
            .setName(Cop1TcPacketHandler.OPTION_BYPASS.getId())
            .setValue(commandOption.coerceValue(v))
            .build());

        return pc;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedCmdName;
    }

    public byte[] getFilePart() {
        return filePart;
    }

    public long getPartSequenceNumber() {
        return partSequenceNumber;
    }

}
