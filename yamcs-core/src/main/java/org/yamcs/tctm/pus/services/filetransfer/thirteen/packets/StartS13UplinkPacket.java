package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.tctm.ccsds.Cop1TcPacketHandler;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13OutgoingTransfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;
import org.yamcs.yarch.Tuple;


public class StartS13UplinkPacket extends UplinkS13Packet {
    protected long partSequenceNumber;
    protected byte[] filePart;
    
    private int filePartActualSize;
    private static final int filePartMDbSize = S13OutgoingTransfer.maxDataSize;

    public StartS13UplinkPacket(S13TransactionId transactionId, long partSequenceNumber, String fullyQualifiedCmdName, byte[] filePart) {
        super(transactionId, fullyQualifiedCmdName);

        this.partSequenceNumber = partSequenceNumber;

        // DO NOT SWAP THE LINES
        this.filePartActualSize = filePart.length;

        // Pad the File Part to match the size specified in the MDb
        this.filePart = padByteArray(filePart, filePartMDbSize, (byte) 0xFF);
    }

    public byte[] padByteArray(byte[] originalBytes, int desiredSize, byte paddingByte) {
        if (originalBytes.length >= desiredSize) {
            return Arrays.copyOf(originalBytes, originalBytes.length);
        }

        byte[] paddedBytes = new byte[desiredSize];
        System.arraycopy(originalBytes, 0, paddedBytes, 0, originalBytes.length);

        Arrays.fill(paddedBytes, originalBytes.length, paddedBytes.length, paddingByte);
        return paddedBytes;
    }

    @Override
    public PreparedCommand generatePreparedCommand(OngoingS13Transfer trans) throws BadRequestException, InternalServerErrorException {
        Map<String, Object> assignments = new LinkedHashMap<>();

        assignments.put("Pus_Acknowledgement_Flags", "Acceptance | Completion");
        assignments.put("Large_Message_Trasaction_Identifier", transactionId.getLargePacketTransactionId());
        assignments.put("Part_Sequence_Number", partSequenceNumber);
        assignments.put("File_Part", filePart);

        PreparedCommand pc = trans.createS13Telecommand(fullyQualifiedCmdName, assignments, trans.getCommandReleaseUser());
        // Set extra options
        pc.setAttribute(Cop1TcPacketHandler.OPTION_BYPASS.getId(), S13OutgoingTransfer.cop1Bypass);
        pc.setAttribute("FilePartSize", filePartActualSize);

        return pc;
    }

    public byte[] getFilePart() {
        return filePart;
    }

    public long getPartSequenceNumber() {
        return partSequenceNumber;
    }

}
