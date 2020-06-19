package org.yamcs.cfdp;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.events.EventProducer;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public class CfdpIncomingTransfer extends CfdpTransfer {

    private static final Logger log = LoggerFactory.getLogger(CfdpIncomingTransfer.class);

    private enum IncomingTransferState {
        START, METADATA_RECEIVED, FILEDATA_RECEIVED, EOF_RECEIVED, RESENDING, FINISHED_SENT, FINISHED_ACK_RECEIVED
    }

    private IncomingTransferState incomingTransferState;
    private Bucket incomingBucket = null;
    private String objectName;
    private long expectedFileSize;
    private long expectedChecksum;

    private DataFile incomingDataFile;
    MetadataPacket metadataPacket;

    public CfdpIncomingTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, YConfiguration config,
            MetadataPacket packet, Stream cfdpOut, Bucket target, EventProducer eventProducer,
            TransferMonitor monitor) {
        this(yamcsInstance, executor, config, packet.getHeader().getTransactionId(), cfdpOut, target, eventProducer,
                monitor);
        // create a new empty data file
        incomingDataFile = new DataFile(packet.getFileLength());
        this.acknowledged = packet.getHeader().isAcknowledged();
        this.incomingTransferState = IncomingTransferState.START;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        objectName = "received_" + dateFormat.format(new Date(startTime));
        expectedFileSize = packet.getFileLength();
        this.metadataPacket = packet;
    }

    public CfdpIncomingTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, YConfiguration config,
            CfdpTransactionId id, Stream cfdpOut, Bucket target, EventProducer eventProducer, TransferMonitor monitor) {
        super(yamcsInstance, executor, config, id, cfdpOut, eventProducer, monitor);
        incomingBucket = target;
        rescheduleInactivityTimer();
    }

    @Override
    public void processPacket(CfdpPacket packet) {
        executor.execute(() -> doProcessPacket(packet));
    }

    private void doProcessPacket(CfdpPacket packet) {
        if (log.isDebugEnabled()) {
            log.debug("CFDP transaction {}, received PDU: {}", cfdpTransactionId, packet);
            log.trace("{}", StringConverter.arrayToHexString(packet.toByteArray(), true));
        }
        rescheduleInactivityTimer();
        if (packet.getHeader().isFileDirective()) {
            switch (((FileDirective) packet).getFileDirectiveCode()) {
            case Metadata:
                // do nothing, already processed this (constructor)
                break;
            case EOF:
                EofPacket  eofPacket = (EofPacket) packet;
                expectedChecksum = eofPacket.getFileChecksum();
                
                List<SegmentRequest> missingSegments = incomingDataFile.getMissingChunks();
                log.debug("EOF received, having {} missing segments", missingSegments.size());
                if (this.acknowledged) {
                    sendAckEofPacket(packet);
                    // check completeness
                    if (missingSegments.isEmpty()) {
                        onFileCompleted(packet);
                    } else {
                        sendNakPacket(packet, missingSegments);
                        this.incomingTransferState = IncomingTransferState.RESENDING;
                    }
                } else {
                    if (missingSegments.isEmpty()) {
                        onFileCompleted(packet);
                    } else {
                        failTransfer("EOF received but missing " + missingSegments.size() + " segments");
                        saveFileInBucket(false, missingSegments);
                    }
                }
                break;
            case ACK:
                AckPacket ack = (AckPacket) packet;
                if (ack.getDirectiveCode() == FileDirectiveCode.Finished) {
                    this.incomingTransferState = IncomingTransferState.FINISHED_ACK_RECEIVED;
                    changeState(TransferState.COMPLETED);
                  
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
            monitor.stateChanged(this);

            if (this.incomingTransferState == IncomingTransferState.RESENDING) {
                    if (incomingDataFile.isComplete()) {
                        onFileCompleted(packet);
                        this.incomingTransferState = IncomingTransferState.FINISHED_SENT;
                    }
                }
            }
        }

    private void onFileCompleted(CfdpPacket packet) {
        // verify checksum
        if (expectedChecksum == incomingDataFile.getChecksum()) {
            if(acknowledged) {
                sendFinishedPacket(packet, ConditionCode.NoError);
                this.incomingTransferState = IncomingTransferState.FINISHED_SENT;
            } else {
                changeState(TransferState.COMPLETED);
            }
            saveFileInBucket(false, Collections.emptyList());
        } else {
            log.warn("File checksum failure; EOF packet indicates {} while data received has {}", expectedChecksum, incomingDataFile.getChecksum());
            if (acknowledged) {
                sendFinishedPacket(packet, ConditionCode.FileChecksumFailure);
            }
            saveFileInBucket(true, Collections.emptyList());
            changeState(TransferState.FAILED);
        } 
    }

    protected void onInactivityTimerExpiration() {
        failTransfer("inactivity timeout while in " + incomingTransferState + " state");
    }

    private void saveFileInBucket(boolean checksumError, List<SegmentRequest> missingSegments) {
        try {
            Map<String, String> metadata = null;
            if (!missingSegments.isEmpty()) {
                metadata = new HashMap<>();
                metadata.put("missingSegments", missingSegments.toString());
            }
            if(checksumError) {
                if(metadata==null) {
                    metadata = new HashMap<>();
                }
                metadata.put("checksumError", "true");
            }
            incomingBucket.putObject(getObjectName(), null, metadata, incomingDataFile.getData());
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

    private void sendFinishedPacket(CfdpPacket packet, ConditionCode code) {

        FinishedPacket fpck;

        if (code == ConditionCode.NoError) {
            fpck = new FinishedPacket(
                    ConditionCode.NoError,
                    true, // data complete
                    FileStatus.SuccessfulRetention,
                    null,
                    getHeader(packet));
        } else {
            fpck = new FinishedPacket(
                    code,
                    false, // data complete
                    FileStatus.DeliberatelyDiscarded,
                    null,
                    getHeader(packet));
        }
        sendPacket(fpck);
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
}
