package org.yamcs.tctm.pus.services.filetransfer.thirteen.requests;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.ServiceThirteen;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.StartS13DownlinkPacket;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.UplinkS13Packet;
import org.yamcs.protobuf.TransferDirection;

public class PutRequest extends S13Request {

    // Required fields
    private final long remoteId;

    // Optional fields
    private String sourceFileName;
    private String destinationFileName;

    // ========== Extra fields ==========
    private UplinkS13Packet fdrPacket;

    public PutRequest(long remoteId) {
        super(S13RequestType.PUT);
        this.remoteId = remoteId;
    }

    public PutRequest(long remoteId, String sourceFileName, String destinationFileName) {
        this(remoteId);
        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
    }

    /** 
     * Generate relevant header and metadata the put request
     * (Only implemented for Messages To User currently)
     * 
     * @param initiatorEntityId
     * @param transferId
     * @param largePacketTransactionId
     * @param checksumType
     * @param config
     * @return
     */
    public S13TransactionId process(long initiatorEntityId, long transferId, long largePacketTransactionId, YConfiguration config) {
        S13TransactionId s13TransactionId = new S13TransactionId(initiatorEntityId, transferId, largePacketTransactionId, TransferDirection.UPLOAD);

        String fullyQualifiedCmdName = ServiceThirteen.constructFullyQualifiedCmdName(ServiceThirteen.startDownlinkCmdName, largePacketTransactionId);
        fdrPacket = new StartS13DownlinkPacket(s13TransactionId, fullyQualifiedCmdName);
        
        return s13TransactionId;
    }

    public long getRemoteId() {
        return remoteId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getDestinationFileName() {
        return destinationFileName;
    }

    // ========== Extra getters ==========
    public UplinkS13Packet getFileDownloadRequestPacket() {
        return fdrPacket;
    }

    public int getFileLength() {
        return 0;
    }

    public byte[] getFileData() {
        return new byte[0];
    }

    public long getChecksum() {
        return 0;
    }
    // ===================================

}
