package org.yamcs.cfdp;

import static org.yamcs.cfdp.CfdpService.ETYPE_FIN_LIMIT_REACHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_COMPLETED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_FINISHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_META;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_RESUMED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_SUSPENDED;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.YConfiguration;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.DirectoryListingResponse;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.OriginatingTransactionId;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.cfdp.pdu.TLV;
import org.yamcs.events.EventProducer;
import org.yamcs.filetransfer.FileSaveHandler;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace;

public class CfdpIncomingTransfer extends OngoingCfdpTransfer {
    private final FileSaveHandler fileSaveHandler;
    private CfdpTransactionId originatingTransactionId;
    private DirectoryListingResponse directoryListingResponse;

    private enum InTxState {
        /**
         * Receives metadata, data and EOF.
         * <p>
         * Sends NAK with missing segments.
         * <p>
         * Goes into FIN as soon as the EOF and all data has been received.
         * <p>
         * Also goes into FIN when an error is encountered or if the transaction is cancelled.
         */
        RECEIVING_DATA,
        /**
         * Send Finished PDUs until ack then go into COMPLETED.
         *
         */
        FIN,
        /**
         *
         */
        COMPLETED
    }

    private InTxState inTxState = InTxState.RECEIVING_DATA;

    private String originalObjectName;

    private DataFile incomingDataFile;
    MetadataPacket metadataPacket;
    EofPacket eofPacket;
    final Timer finTimer;
    Timer checkTimer;

    FinishedPacket finPacket;

    CfdpHeader directiveHeader;

    final long maxFileSize;
    /**
     * How often in millisec we should send the NAK PDUs
     */
    final int nakTimeout;

    /**
     * How many times to sent the NAK PDUs
     */
    final int nakLimit;

    /**
     * If true send EOF ACK even when suspended
     */
    final boolean ackEofWhileSuspended;

    int nakCount = 0;
    long lastNakSentTime = 0;
    long lastNakDataSize;

    /**
     * used to limit the number of NAKs sent in one PDU
     */
    int maxPduDataSize;

    /**
     * If true, send NAK before receiving the EOF
     */
    final boolean immediateNak;

    /**
     * if true, we have to end the transaction with a Finished packet.
     * <p>
     * This is always true for Class 2 transactions but can also be requested for Class 1 transactions.
     */
    boolean needsFinish;

    private boolean suspended = false;

    // this will be set if the transfer is cancelled while suspended, either by user request or by EOF
    ConditionCode cancelUponResume;

    // in case we start a transaction before receiving the metadata, this list will save the packets which will be
    // processed after we have the metadata
    List<CfdpPacket> queuedPackets = new ArrayList<>();

    public CfdpIncomingTransfer(String yamcsInstance, long id, long creationTime, ScheduledThreadPoolExecutor executor,
            YConfiguration config, CfdpHeader hdr, Stream cfdpOut, FileSaveHandler fileSaveHandler,
            EventProducer eventProducer, TransferMonitor monitor,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions) {
        super(yamcsInstance, id, creationTime, executor, config, hdr.getTransactionId(), hdr.getDestinationId(),
                cfdpOut, eventProducer, monitor, faultHandlerActions);
        this.fileSaveHandler = fileSaveHandler;
        rescheduleInactivityTimer();
        long finAckTimeout = config.getLong("finAckTimeout", 10000l);
        int finAckLimit = config.getInt("finAckLimit", 5);

        this.acknowledged = hdr.isAcknowledged();
        this.finTimer = new Timer(executor, finAckLimit, finAckTimeout);
        this.maxFileSize = config.getLong("maxFileSize", 100 * 1024 * 1024l);
        this.nakTimeout = config.getInt("nakTimeout", 5000);
        this.nakLimit = config.getInt("nakLimit", -1);
        this.immediateNak = config.getBoolean("immediateNak", true);
        this.ackEofWhileSuspended = config.getBoolean("ackEofWhileSuspended", true);
        var maxPduSize = config.getInt("maxPduSize", 512);

        if (!acknowledged) {
            long checkAckTimeout = config.getLong("checkAckTimeout", 10000l);
            int checkAckLimit = config.getInt("checkAckLimit", 5);
            checkTimer = new Timer(executor, checkAckLimit, checkAckTimeout);
        }

        // this header will be used for all the outgoing PDUs; copy all settings from the incoming PDU header
        this.directiveHeader = new CfdpHeader(
                true, // file directive
                true, // towards sender
                acknowledged,
                false, // no CRC
                hdr.getEntityIdLength(),
                hdr.getSequenceNumberLength(),
                hdr.getSourceId(),
                hdr.getDestinationId(),
                hdr.getSequenceNumber());

        this.maxPduDataSize = maxPduSize - directiveHeader.getLength();
        needsFinish = acknowledged;
        incomingDataFile = new DataFile(-1);
    }

    @Override
    public void processPacket(CfdpPacket packet) {
        executor.execute(() -> doProcessPacket(packet));
    }

    private void sendOrScheduleNak() {
        if (inTxState != InTxState.RECEIVING_DATA || suspended) {
            return;
        }

        if (!eofReceived() && !immediateNak) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastNakSentTime < nakTimeout) {
            return;
        }

        if (sendNak()) {
            lastNakSentTime = now;
        }

        executor.schedule(this::sendOrScheduleNak, nakTimeout, TimeUnit.MILLISECONDS);
    }

    private boolean sendNak() {
        List<SegmentRequest> missingSegments = incomingDataFile.getMissingChunks(eofReceived() && metadataReceived());

        if (!metadataReceived()) {
            missingSegments.add(0, new SegmentRequest(0, 0));
        }
        if (missingSegments.isEmpty()) {
            return false;
        }
        // if there has been some data received since last NAK, reset the counter
        // (this doesn't account for metadata but let's not be picky..)
        long size = incomingDataFile.getReceivedSize();
        if (size > lastNakDataSize) {
            lastNakDataSize = size;
            nakCount = 0;
        }
        nakCount++;
        if (nakLimit > 0 && nakCount > nakLimit) {
            log.warn("TXID{} NAK limit reached", cfdpTransactionId);
            handleFault(ConditionCode.NAK_LIMIT_REACHED);
            return false;
        }

        var maxNumSeg = NakPacket.maxNumSegments(maxPduDataSize);
        if (missingSegments.size() > maxNumSeg) {
            missingSegments = missingSegments.subList(0, maxNumSeg);
        }
        sendPacket(new NakPacket(
                missingSegments.get(0).getSegmentStart(),
                missingSegments.get(missingSegments.size() - 1).getSegmentEnd(),
                missingSegments,
                directiveHeader));
        return true;
    }

    private void doProcessPacket(CfdpPacket packet) {
        log.debug("TXID{} received PDU: {}", cfdpTransactionId, packet);

        if (log.isTraceEnabled()) {
            log.trace("{}", StringConverter.arrayToHexString(packet.toByteArray(), true));
        }

        if (inTxState == InTxState.RECEIVING_DATA) {
            rescheduleInactivityTimer();
        }

        if (packet instanceof MetadataPacket) {
            processMetadata((MetadataPacket) packet);
        } else if (packet instanceof FileDataPacket) {
            processFileDataPacket((FileDataPacket) packet);
        } else if (packet instanceof EofPacket) {
            processEofPacket((EofPacket) packet);
        } else if (packet instanceof AckPacket) {
            processAckPacket((AckPacket) packet);
        } else {
            log.info("TXID{} received unexpected packet {}", cfdpTransactionId, packet);
        }
    }

    private void processMetadata(MetadataPacket packet) {
        if (metadataPacket != null || inTxState != InTxState.RECEIVING_DATA) {
            log.debug("TXID{} Ignoring metadata packet {}", cfdpTransactionId, packet);
            return;
        }
        if (packet.getChecksumType() != ChecksumType.MODULAR) {
            log.warn("TXID{} received metadata indicating unsupported checksum type {}", cfdpTransactionId,
                    packet.getChecksumType());
            handleFault(ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
            return;
        }
        long fileSize = packet.getFileLength();
        if (fileSize > maxFileSize) {
            String err = String.format(
                    "received metadata with file size %.02f KB exceeding the maximum allowed %.02f KB",
                    fileSize / 1024.0, maxFileSize / 1024.0);

            log.warn("TXID{} {}", cfdpTransactionId, err);
            pushError(err);

            handleFault(ConditionCode.FILE_SIZE_ERROR);
            return;
        }
        long eof = incomingDataFile.endOfFileOffset();
        if (eof > fileSize) {
            log.warn("TXID{} Received size {} but maximum offset of existing segments is {}",
                    cfdpTransactionId, fileSize, eof);
            handleFault(ConditionCode.FILE_SIZE_ERROR);
            return;
        }
        this.metadataPacket = packet;

        transferType = getTransferType(metadataPacket);

        if (metadataPacket.getOptions() != null) {
            for (TLV option : metadataPacket.getOptions()) {
                if (option instanceof OriginatingTransactionId) {
                    this.originatingTransactionId = ((OriginatingTransactionId) option).toCfdpTransactionId();
                } else if (option instanceof DirectoryListingResponse) {
                    this.directoryListingResponse = (DirectoryListingResponse) option;
                }
            }
        }

        needsFinish = acknowledged || packet.closureRequested();

        incomingDataFile.setSize(fileSize);

        this.acknowledged = packet.getHeader().isAcknowledged();
        originalObjectName = !packet.getDestinationFilename().isEmpty() ? packet.getDestinationFilename()
                : packet.getSourceFilename();

        sendInfoEvent(ETYPE_TRANSFER_META, "Received metadata: " + toEventMsg(packet));

        try {
            if (originatingTransactionId != null) {
                fileSaveHandler.processOriginatingTransactionId(originatingTransactionId);
            }
            fileSaveHandler.setObjectName(directoryListingResponse == null ? originalObjectName : null);

            Tablespace.BucketProperties props = fileSaveHandler.getBucket().getProperties();
            if (props.getMaxSize() - props.getSize() < fileSize) {
                throw new IOException("File too big for bucket '" + getBucketName() + "' (" + fileSize + " bytes for "
                        + (props.getMaxSize() - props.getSize()) + " available)");
            }

            checkFileComplete();
        } catch (IOException e) {
            handleFault(ConditionCode.FILESTORE_REJECTION);
            log.warn(e.getMessage());
            pushError(e.getMessage());
        }
    }

    private void processAckPacket(AckPacket ack) {
        if (inTxState == InTxState.RECEIVING_DATA
                || ack.getDirectiveCode() != FileDirectiveCode.FINISHED) {
            log.warn("TXID{} ignoring bogus ACK {}", cfdpTransactionId, ack);
            return;
        }

        finTimer.cancel();
        if (inTxState == InTxState.FIN) {
            complete(finPacket.getConditionCode());
        } else {
            log.debug("TXID{} ignoring ACK {}", cfdpTransactionId, ack);
        }
    }

    private void processEofPacket(EofPacket packet) {
        if (acknowledged && (!suspended || ackEofWhileSuspended)) {
            sendPacket(getAckEofPacket(packet.getConditionCode()));
        }

        if (eofPacket != null || inTxState != InTxState.RECEIVING_DATA) {
            log.debug("TXID{} ignoring packet {}", cfdpTransactionId, packet);
            return;
        }
        eofPacket = packet;

        if (eofPacket.getConditionCode() == ConditionCode.NO_ERROR) {
            if (!suspended) {
                checkFileComplete();
            }
        } else if (eofPacket.getConditionCode() == ConditionCode.CANCEL_REQUEST_RECEIVED) {
            pushError("Canceled by the Sender");
            complete(ConditionCode.CANCEL_REQUEST_RECEIVED);
        } else {
            log.warn("TXID{} EOF received indicating error {}", cfdpTransactionId, eofPacket.getConditionCode());
            handleFault(eofPacket.getConditionCode());
        }

        if (!acknowledged && inTxState == InTxState.RECEIVING_DATA && !suspended) {
            // start the checkTimer
            checkTimer.start(this::checkFileComplete, () -> {
                log.warn("TXID{} check limit reached", cfdpTransactionId);
                handleFault(ConditionCode.CHECK_LIMIT_REACHED);
            });
        }
    }

    private void checkFileComplete() {
        if (incomingDataFile.isComplete() && eofReceived()) {
            onFileCompleted();
        } else {
            if (acknowledged) {
                sendOrScheduleNak();
            }
        }
    }

    private void processFileDataPacket(FileDataPacket fdp) {
        if (inTxState != InTxState.RECEIVING_DATA) {
            log.debug("TXID{} ignoring packet {}", cfdpTransactionId, fdp);
            return;
        }

        long fileSize = incomingDataFile.getSize();
        if (fileSize > 0) {
            if (fdp.getEndOffset() > fileSize) {
                String err = String.format("Received data file whose end offset %d is larger than the file size %d",
                        fdp.getEndOffset(), fileSize);
                log.warn("TXID{} {}", cfdpTransactionId, err);
                pushError(err);
                handleFault(ConditionCode.FILE_SIZE_ERROR);
            }
        } else {
            if (fdp.getEndOffset() > maxFileSize) {
                String err = String.format(
                        "Received data file whose end offset %d is larger than the maximum file size %d",
                        fdp.getEndOffset(), maxFileSize);
                pushError(err);
                log.warn("TXID{} {}", cfdpTransactionId, err);
                handleFault(ConditionCode.FILE_SIZE_ERROR);
            }
        }

        incomingDataFile.addSegment(fdp);
        monitor.stateChanged(this);
        checkFileComplete();
    }

    private void onFileCompleted() {
        // verify checksum
        long expectedChecksum = eofPacket.getFileChecksum();
        if (expectedChecksum == incomingDataFile.getChecksum()) {
            log.info("TXID{} file completed, checksum OK", cfdpTransactionId);
            if (needsFinish) {
                finish(ConditionCode.NO_ERROR);
            } else {
                complete(ConditionCode.NO_ERROR);
            }
            saveFile(false, Collections.emptyList());
            sendInfoEvent(ETYPE_TRANSFER_FINISHED,
                    " downlink finished and saved in " + getBucketName() + "/" + getObjectName());
        } else {
            log.warn("TXID{} file checksum failure; EOF packet indicates {} while data received has {}",
                    cfdpTransactionId, expectedChecksum, incomingDataFile.getChecksum());
            saveFile(true, Collections.emptyList());
            sendWarnEvent(ETYPE_TRANSFER_FINISHED,
                    " checksum failure; corrupted file saved in " + getBucketName() + "/" + getObjectName());
            handleFault(ConditionCode.FILE_CHECKSUM_FAILURE);
        }
    }

    private void finish(ConditionCode code) {
        if (inTxState == InTxState.FIN) {
            throw new IllegalStateException("already in FINISHED state");
        }
        assert (!suspended);

        log.debug("TXID{} finishing with code {}", cfdpTransactionId, code);
        cancelInactivityTimer();
        if (!acknowledged) {
            checkTimer.cancel();
        }

        finPacket = getFinishedPacket(code);
        this.inTxState = InTxState.FIN;
        if (code != ConditionCode.NO_ERROR) {
            changeState(TransferState.CANCELLING);
        }

        sendFin();
    }

    private void sendFin() {
        sendPacket(finPacket);

        finTimer.start(() -> sendPacket(finPacket),
                () -> {
                    sendWarnEvent(ETYPE_FIN_LIMIT_REACHED,
                            "resend attempts (" + finTimer.maxNumAttempts + ") of Finished PDU reached");
                    if (finPacket.getConditionCode() == ConditionCode.NO_ERROR) {
                        pushError("File was received OK but the Finished PDU has not been acknowledged");
                        complete(ConditionCode.ACK_LIMIT_REACHED);
                    } else {
                        pushError("The Finished PDU has not been acknowledged");
                        complete(ConditionCode.ACK_LIMIT_REACHED);
                    }
                });
    }

    private void complete(ConditionCode conditionCode) {
        inTxState = InTxState.COMPLETED;
        if (!acknowledged) {
            checkTimer.cancel();
        }

        if (conditionCode == ConditionCode.NO_ERROR) {
            changeState(TransferState.COMPLETED);
            sendInfoEvent(ETYPE_TRANSFER_COMPLETED, " transfer completed (ack received from remote) successfully");
        } else {
            if (errors.isEmpty()) {
                pushError(conditionCode.toString());
            }
            sendWarnEvent(ETYPE_TRANSFER_COMPLETED, " transfer completed unsuccessfully: " + getFailuredReason());
            changeState(TransferState.FAILED);
        }
    }

    private void handleFault(ConditionCode conditionCode) {
        switch (inTxState) {
        case RECEIVING_DATA:
            FaultHandlingAction action = getFaultHandlingAction(conditionCode);
            switch (action) {
            case ABANDON:
                complete(conditionCode);
                break;
            case CANCEL:
                cancel(conditionCode);
                break;
            case SUSPEND:
                suspend();
                break;
            }
            break;
        case FIN:
            complete(conditionCode);
            break;
        case COMPLETED:
            break;
        }
    }

    @Override
    protected void onInactivityTimerExpiration() {
        log.warn("TXID{} inactivity timer expired, state: {}", cfdpTransactionId, inTxState);

        switch (inTxState) {
        case RECEIVING_DATA:
            handleFault(ConditionCode.INACTIVITY_DETECTED);
            break;
        case FIN:
        case COMPLETED:
            log.error("TXID{} Illegal state", cfdpTransactionId);
            break;
        }
    }

    @Override
    protected void suspend() {
        if (inTxState == InTxState.COMPLETED) {
            log.info("TXID{} transfer finished, suspend ignored", cfdpTransactionId);
            return;
        }
        log.info("TXID{} suspending transfer", cfdpTransactionId);
        sendInfoEvent(ETYPE_TRANSFER_SUSPENDED, "transfer suspended");
        changeState(TransferState.PAUSED);
        finTimer.cancel();
        if (!acknowledged) {
            checkTimer.cancel();
        }
        suspended = true;
    }

    @Override
    protected void resume() {
        if (!suspended) {
            log.info("TXID{} resume called while not suspended, ignoring", cfdpTransactionId);
            return;
        }
        suspended = false;
        if (inTxState == InTxState.COMPLETED) {
            // it is possible the transfer has finished while being suspended
            log.info("TXID{} transfer finished, resume ignored", cfdpTransactionId);
            return;
        }
        if (cancelUponResume != null) {
            cancel(cancelUponResume);
            cancelUponResume = null;
            return;
        }
        log.info("TXID{} resuming transfer", cfdpTransactionId);

        sendInfoEvent(ETYPE_TRANSFER_RESUMED, "transfer resumed");
        if (inTxState == InTxState.RECEIVING_DATA) {
            checkFileComplete();
        } else if (inTxState == InTxState.FIN) {
            sendFin();
        }

        if (!acknowledged && inTxState == InTxState.RECEIVING_DATA) {
            // start the checkTimer
            checkTimer.start(this::checkFileComplete, () -> {
                log.warn("TXID{} check limit reached", cfdpTransactionId);
                handleFault(ConditionCode.CHECK_LIMIT_REACHED);
            });
        }
        changeState(TransferState.RUNNING);
    }

    @Override
    protected void cancel(ConditionCode code) {
        if (inTxState == InTxState.RECEIVING_DATA) {
            if (needsFinish) {
                if (suspended) {
                    cancelUponResume = code;
                } else {
                    finish(code);
                }
            } else {
                complete(code);
            }
        } else {
            log.debug("TXID{} ignoring cancel, wrong state", cfdpTransactionId);
        }
    }

    private void saveFile(boolean checksumError, List<SegmentRequest> missingSegments) {
        if (directoryListingResponse != null) {
            log.debug("TXID{} Ignoring save action for Directory Listing Response", cfdpTransactionId);
            return;
        }

        Map<String, String> metadata = null;
        if (!missingSegments.isEmpty()) {
            metadata = new HashMap<>();
            metadata.put("missingSegments", missingSegments.toString());
        }
        if (checksumError) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put("checksumError", "true");
        }

        // TODO: source data in metadata

        fileSaveHandler.saveFile(incomingDataFile, metadata, originatingTransactionId);
    }

    private AckPacket getAckEofPacket(ConditionCode code) {
        return new AckPacket(
                FileDirectiveCode.EOF,
                FileDirectiveSubtypeCode.FINISHED_BY_WAYPOINT_OR_OTHER,
                code,
                TransactionStatus.ACTIVE,
                directiveHeader);
    }

    private FinishedPacket getFinishedPacket(ConditionCode code) {

        FinishedPacket fpck;

        if (code == ConditionCode.NO_ERROR) {
            fpck = new FinishedPacket(
                    ConditionCode.NO_ERROR,
                    true, // data complete
                    FileStatus.SUCCESSFUL_RETENTION,
                    null,
                    directiveHeader);
        } else {
            fpck = new FinishedPacket(
                    code,
                    true, // data complete
                    FileStatus.DELIBERATELY_DISCARDED,
                    null,
                    directiveHeader);
        }
        return fpck;
    }

    private boolean metadataReceived() {
        return metadataPacket != null;
    }

    private boolean eofReceived() {
        return eofPacket != null;
    }

    @Override
    public long getInitiatorEntityId() {
        return directiveHeader.getSourceId();
    }

    @Override
    public String getBucketName() {
        return fileSaveHandler.getBucketName();
    }

    @Override
    public String getObjectName() {
        return fileSaveHandler.getObjectName() != null || directoryListingResponse != null
                ? fileSaveHandler.getObjectName()
                : originalObjectName;
    }

    public String getOriginalObjectName() {
        return originalObjectName;
    }

    @Override
    public String getRemotePath() {
        return (metadataPacket == null) ? null : metadataPacket.getSourceFilename();
    }

    @Override
    public TransferDirection getDirection() {
        return TransferDirection.DOWNLOAD;
    }

    @Override
    public long getTotalSize() {
        return incomingDataFile.getSize();
    }

    @Override
    public long getTransferredSize() {
        return incomingDataFile.getReceivedSize();
    }

    public DirectoryListingResponse getDirectoryListingResponse() {
        return directoryListingResponse;
    }

    public byte[] getFileData() {
        return incomingDataFile.getData();
    }

    public CfdpTransactionId getOriginatingTransactionId() {
        return originatingTransactionId;
    }

}
