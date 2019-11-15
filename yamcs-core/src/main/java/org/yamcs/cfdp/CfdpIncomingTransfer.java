package org.yamcs.cfdp;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.EventProducer;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FileStoreResponse;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public class CfdpIncomingTransfer extends CfdpTransfer {

    private static final Logger log = LoggerFactory.getLogger(CfdpIncomingTransfer.class);

    private enum ReceiverTransferState {
        START, METADATA_RECEIVED, FILEDATA_RECEIVED, EOF_RECEIVED, RESENDING, FINISHED_SENT, FINISHED_ACK_RECEIVED
    }

    private ReceiverTransferState currentState;
    private Bucket incomingBucket = null;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    private String objectName;
    private long expectedFileSize;

    private DataFile incomingDataFile;
    MetadataPacket metadataPacket;
    
    public CfdpIncomingTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, MetadataPacket packet, Stream cfdpOut,
            Bucket target, EventProducer eventProducer) {
        this(yamcsInstance, executor, packet.getHeader().getTransactionId(), cfdpOut, target, eventProducer);
        // create a new empty data file
        incomingDataFile = new DataFile(packet.getPacketLength());
        this.acknowledged = packet.getHeader().isAcknowledged();
        this.currentState = ReceiverTransferState.START;
        objectName = "received_" + dateFormat.format(new Date());
        expectedFileSize = packet.getPacketLength();
        this.metadataPacket = packet;
    }

    public CfdpIncomingTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, CfdpTransactionId id, Stream cfdpOut,
            Bucket target, EventProducer eventProducer) {
        super(yamcsInstance, executor, id, cfdpOut, eventProducer);
        incomingBucket = target;
    }

    @Override
    public void step() {
        switch (currentState) {
        case START:
            this.currentState = ReceiverTransferState.START;
            break;
        case METADATA_RECEIVED:
            // nothing to do, we're waiting for data
            changeState(TransferState.RUNNING);
            break;
        case FILEDATA_RECEIVED:
            // nothing to do, we're waiting for more data or an EOF
            // TODO, timeout + request resend
            break;
        case EOF_RECEIVED:
            // unreachable, virtual state, after receiving EOF, Finished packet is immediately
            // sent back and state updated accordingly
            break;
        case FINISHED_SENT:
            // nothing to do, awaiting the ack
            break;
        case RESENDING:
            // nothing to do, waiting for further packets
            break;
        case FINISHED_ACK_RECEIVED:
            changeState(TransferState.COMPLETED);
            break;
        }
    }

    @Override
    public void processPacket(CfdpPacket packet) {
        if(log.isDebugEnabled()) {
            log.debug("CFDP transaction {}, received PDU: {}", cfdpTransactionId, packet);
            log.trace("{}", StringConverter.arrayToHexString(packet.toByteArray(), true));
        }
        if (packet.getHeader().isFileDirective()) {
            switch (((FileDirective) packet).getFileDirectiveCode()) {
            case Metadata:
                // do nothing, already processed this (constructor)
                break;
            case EOF:
                if (this.acknowledged) {
                    sendAckEofPacket(packet);
                    // check completeness
                    List<SegmentRequest> missingSegments = incomingDataFile.getMissingChunks();
                    if (missingSegments.isEmpty()) {
                        sendFinishedPacket(packet);
                        this.currentState = ReceiverTransferState.FINISHED_SENT;
                    } else {
                        sendNakPacket(packet, missingSegments);
                        this.currentState = ReceiverTransferState.RESENDING;
                    }
                } else {
                    this.state = TransferState.COMPLETED;
                    saveFileInBucket();
                }
                break;
            case ACK:
                AckPacket ack = (AckPacket) packet;
                if (ack.getDirectiveCode() == FileDirectiveCode.Finished) {
                    this.currentState = ReceiverTransferState.FINISHED_ACK_RECEIVED;
                    this.state = TransferState.COMPLETED;
                    saveFileInBucket();
                } else {
                    // we're not expecting any other ACK, so log and ignore
                    log.info("received unexpected ACK, with directive code ", ack.getDirectiveCode().name());
                }
                break;
            default:
                // we're not expecting any other FileDirective packet, so log and ignore
                log.info("received unexpected File Directive packet of type ",
                        ((FileDirective) packet).getFileDirectiveCode().name());
            }
        } else {
            FileDataPacket fdp = (FileDataPacket) packet;
            incomingDataFile.addSegment(new DataFileSegment(fdp.getOffset(), fdp.getData()));
            if (this.currentState == ReceiverTransferState.RESENDING) {
                if (this.acknowledged) {
                    if (incomingDataFile.isComplete()) {
                        sendFinishedPacket(packet);
                        this.currentState = ReceiverTransferState.FINISHED_SENT;
                    }
                }
            }
        }
    }

    private void saveFileInBucket() {
        try {
            incomingBucket.putObject(getObjectName(), null, null, incomingDataFile.getData());
        } catch (IOException e) {
            throw new RuntimeException("cannot save incoming file in bucket " + incomingBucket.getName(), e);
        }
    }

    private void sendAckEofPacket(CfdpPacket packet) {
        sendPacket(new AckPacket(
                FileDirectiveCode.EOF,
                FileDirectiveSubtypeCode.FinishedByWaypointOrOther,
                ConditionCode.NoError,
                TransactionStatus.Active,
                getHeader(packet)));
    }

    private void sendNakPacket(CfdpPacket packet, List<SegmentRequest> missingSegments) {
        sendPacket(new NakPacket(
                missingSegments.get(0).getSegmentStart(),
                missingSegments.get(missingSegments.size() - 1).getSegmentEnd(),
                missingSegments,
                getHeader(packet)));
    }

    private void sendFinishedPacket(CfdpPacket packet) {
        sendPacket(new FinishedPacket(
                ConditionCode.NoError,
                true, // generated by end system
                false, // data complete
                FileStatus.SuccessfulRetention,
                new ArrayList<FileStoreResponse>(),
                null,
                getHeader(packet)));
    }

    private CfdpHeader getHeader(CfdpPacket packet) {
        return new CfdpHeader(
                true, // file directive
                true, // towards sender
                acknowledged,
                false, // no CRC
                packet.getHeader().getEntityIdLength(),
                packet.getHeader().getSequenceNumberLength(),
                packet.getHeader().getSourceId(),
                packet.getHeader().getDestinationId(),
                packet.getHeader().getSequenceNumber());
    }

    @Override
    public Bucket getBucket() {
        return incomingBucket;
    }

    @Override
    public String getObjectName() {
        return objectName;
    }

    @Override
    public String getRemotePath() {
        return metadataPacket.getSourceFilename();
    }

    @Override
    public TransferDirection getDirection() {
        return TransferDirection.DOWNLOAD;
    }

    @Override
    public long getTotalSize() {
        return expectedFileSize;
    }

    @Override
    public long getTransferredSize() {
        return incomingDataFile.getReceivedSize();
    }

    @Override
    public boolean cancellable() {
        return false;
    }

    @Override
    public boolean pausable() {
        return false;
    }

    @Override
    public void run() {
        // TODO - we should setup some timers to check the incoming file is delivered ok
    }

    @Override
    public String getFailuredReason() {
        return null;
    }
    
    
 
}
