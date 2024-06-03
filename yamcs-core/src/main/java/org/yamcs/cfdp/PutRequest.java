package org.yamcs.cfdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yamcs.YConfiguration;
import org.yamcs.cfdp.OngoingCfdpTransfer.FaultHandlingAction;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.FileStoreRequest;
import org.yamcs.cfdp.pdu.MessageToUser;
import org.yamcs.cfdp.pdu.MetadataPacket;

/**
 * Put.request (destination CFDP entity ID, [source file name], [destination file name], [segmentation control], [fault
 * handler overrides], [flow label], [transmission mode], [closure requested], [messages to user], [filestore requests])
 */
public class PutRequest extends CfdpRequest {

    // Required fields
    private final long destinationCfdpEntityId;

    // Optional fields
    private String sourceFileName;
    private String destinationFileName;
    private SegmentationControl segmentationControl; // NOT IMPLEMENTED
    private Map<ConditionCode, FaultHandlingAction> faultHandlerOverride; // [[condition code, handler code],...] NOT
                                                                          // IMPLEMENTED
    private String flowLabel; // NOT IMPLEMENTED
    private CfdpPacket.TransmissionMode transmissionMode;
    private boolean closureRequested = false;
    private List<MessageToUser> messagesToUser;
    private List<FileStoreRequest> fileStoreRequests; // NOT IMPLEMENTED

    // ========== Extra fields ==========
    private CfdpHeader header;
    private MetadataPacket metadata;
    // ==================================

    public enum SegmentationControl {
        RECORD_BOUNDARIES_NOT_PRESERVED(0),
        RECORD_BOUNDARIES_PRESERVED(1);

        private final int value;

        SegmentationControl(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    protected PutRequest(long destinationCfdpEntityId) {
        super(CfdpRequestType.PUT);
        this.destinationCfdpEntityId = destinationCfdpEntityId;
    }

    protected PutRequest(long destinationCfdpEntityId, String sourceFileName, String destinationFileName,
            SegmentationControl segmentationControl, Map<ConditionCode, FaultHandlingAction> faultHandlerOverride,
            String flowLabel, CfdpPacket.TransmissionMode transmissionMode, boolean closureRequested,
            List<MessageToUser> messagesToUser, List<FileStoreRequest> fileStoreRequests) {
        this(destinationCfdpEntityId);
        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
        this.segmentationControl = segmentationControl;
        this.faultHandlerOverride = faultHandlerOverride;
        this.flowLabel = flowLabel;
        this.transmissionMode = transmissionMode;
        this.closureRequested = closureRequested;
        this.messagesToUser = messagesToUser;
        this.fileStoreRequests = fileStoreRequests;
    }

    // Constructor for messages to user
    protected PutRequest(long destinationCfdpEntityId, CfdpPacket.TransmissionMode transmissionMode,
            List<MessageToUser> messagesToUser) {
        this(destinationCfdpEntityId);
        this.transmissionMode = transmissionMode;
        this.messagesToUser = messagesToUser;
    }

    /**
     * Generate relevant header and metadata the put request (Only implemented for Messages To User currently)
     *
     * @param initiatorEntityId
     * @param sequenceNumber
     * @param checksumType
     * @param config
     * @return
     */
    public CfdpTransactionId process(long initiatorEntityId, long sequenceNumber, ChecksumType checksumType,
            YConfiguration config) {
        CfdpTransactionId transactionId = new CfdpTransactionId(initiatorEntityId, sequenceNumber);
        // Copy File Procedure
        // fault handlers from PR
        // messages to user & file store requests from PR
        // no source/destination = only metadata
        // transmission mode from PR if specified (Management Information Base otherwise)
        // closure requested from PR if specified (Management Information Base otherwise)

        // TODO: Generalise, only implemented for Messages To User only transaction at the moment
        header = new CfdpHeader(
                true, // file directive
                false, // towards receiver
                isAcknowledged(),
                false, // noCRC
                config.getInt("entityIdLength"),
                config.getInt("sequenceNrLength"),
                initiatorEntityId,
                destinationCfdpEntityId,
                sequenceNumber);

        metadata = new MetadataPacket(
                closureRequested,
                checksumType,
                0,
                "",
                "",
                new ArrayList<>(messagesToUser),
                header);

        return transactionId;
    }

    public long getDestinationCfdpEntityId() {
        return destinationCfdpEntityId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getDestinationFileName() {
        return destinationFileName;
    }

    public SegmentationControl getSegmentationControl() {
        return segmentationControl;
    }

    public Map<ConditionCode, FaultHandlingAction> getFaultHandlerOverride() {
        return faultHandlerOverride;
    }

    public String getFlowLabel() {
        return flowLabel;
    }

    public CfdpPacket.TransmissionMode getTransmissionMode() {
        return transmissionMode;
    }

    public boolean isAcknowledged() {
        return transmissionMode == CfdpPacket.TransmissionMode.ACKNOWLEDGED;
    }

    public boolean isClosureRequested() {
        return closureRequested;
    }

    public List<MessageToUser> getMessagesToUser() {
        return messagesToUser;
    }

    public List<FileStoreRequest> getFileStoreRequests() {
        return fileStoreRequests;
    }

    public CfdpHeader getHeader() {
        return header;
    }

    public MetadataPacket getMetadata() {
        return metadata;
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
}
