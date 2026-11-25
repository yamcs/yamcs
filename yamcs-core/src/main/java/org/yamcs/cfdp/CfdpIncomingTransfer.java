package org.yamcs.cfdp;

import static org.yamcs.cfdp.CfdpService.ETYPE_FIN_LIMIT_REACHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_COMPLETED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_FINISHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_META;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_RESUMED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_SUSPENDED;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.YConfiguration;
import org.yamcs.buckets.Bucket;
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
import org.yamcs.cfdp.protobuf.CfdpHeaderFields;
import org.yamcs.cfdp.protobuf.IncomingTransferSnapshot;
import org.yamcs.cfdp.protobuf.SnapshotDirection;
import org.yamcs.cfdp.protobuf.TransferSnapshot;
import org.yamcs.events.EventProducer;
import org.yamcs.filetransfer.FileSaveHandler;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;

import com.google.protobuf.ByteString;

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

    private final AbstractDataFile incomingDataFile;
    // where the transfer is persisted; null if the transfer is not persisted
    private final CfdpTransferStore store;
    // set when some state not saved at transitions (e.g. NAK counters) changes
    private boolean stateDirty = false;
    private boolean storageReleased = false;
    MetadataPacket metadataPacket;
    EofPacket eofPacket;
    final Timer finTimer;
    Timer checkTimer;
    // used to delay the first Finished PDU such that it is not sent too close to the previous PDU
    ScheduledFuture<?> finSendFuture;

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

    /**
     * Minimum time in millisec between two PDUs sent by the receiver (e.g. between the EOF ACK and the FIN)
     */
    final int sleepBetweenPdus;
    long lastPduSentTime = 0;

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
    // if true, resume automatically when receiving a PDU while suspended
    private final boolean autoResume;

    // this will be set if the transfer is cancelled while suspended, either by user request or by EOF
    ConditionCode cancelUponResume;

    // in case we start a transaction before receiving the metadata, this list will save the packets which will be
    // processed after we have the metadata
    List<CfdpPacket> queuedPackets = new ArrayList<>();

    public CfdpIncomingTransfer(String yamcsInstance, long id, long creationTime, ScheduledThreadPoolExecutor executor,
            YConfiguration config, CfdpHeader hdr, Stream cfdpOut, FileSaveHandler fileSaveHandler,
            EventProducer eventProducer, TransferMonitor monitor,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions) {
        this(yamcsInstance, id, creationTime, executor, config, hdr, cfdpOut, fileSaveHandler, eventProducer, monitor,
                faultHandlerActions, null, new DataFile(-1));
    }

    /**
     * Creates a new incoming transfer whose data is stored in the given data file.
     *
     * @param store
     *            if not null, the state of the transfer is persisted in the store such that it can be restored after a
     *            restart.
     */
    public CfdpIncomingTransfer(String yamcsInstance, long id, long creationTime, ScheduledThreadPoolExecutor executor,
            YConfiguration config, CfdpHeader hdr, Stream cfdpOut, FileSaveHandler fileSaveHandler,
            EventProducer eventProducer, TransferMonitor monitor,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions, CfdpTransferStore store,
            AbstractDataFile dataFile) {
        super(yamcsInstance, id, creationTime, executor, config, hdr.getTransactionId(), hdr.getDestinationId(),
                cfdpOut, eventProducer, monitor, faultHandlerActions);
        this.fileSaveHandler = fileSaveHandler;
        this.store = store;
        this.incomingDataFile = dataFile;
        // the state is saved when the first packet is processed
        this.stateDirty = store != null;
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
        this.autoResume = config.getBoolean("autoResume", false);
        this.sleepBetweenPdus = config.getInt("sleepBetweenPdus", 500);
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

    /**
     * Called when the transfer is resumed by the user: sends a NAK even if no gap has been detected yet, requesting
     * also the data after the last received segment up to the end of the file. This prods the sender to continue a
     * transfer which has been interrupted (e.g. by a loss of signal) before the EOF was received.
     */
    private void sendResumeNak() {
        if (!eofReceived() && !immediateNak) {
            return;
        }
        if (sendNak(metadataReceived())) {
            lastNakSentTime = System.currentTimeMillis();
        }
        executor.schedule(this::sendOrScheduleNak, nakTimeout, TimeUnit.MILLISECONDS);
    }

    private boolean sendNak() {
        return sendNak(eofReceived() && metadataReceived());
    }

    /**
     * @param includeEnd
     *            if true, the segment between the last received data and the end of the file is also requested.
     *            Requires the metadata (file size) to have been received.
     */
    private boolean sendNak(boolean includeEnd) {
        List<SegmentRequest> missingSegments = incomingDataFile.getMissingChunks(includeEnd);

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
        stateDirty = true;
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
        if (stateDirty && inTxState != InTxState.COMPLETED) {
            // first packet or some state has changed
            persistState();
        }
        if (suspended && autoResume && inTxState != InTxState.COMPLETED) {
            log.info("TXID{} PDU received from the sender while suspended, resuming (autoResume)",
                    cfdpTransactionId);
            resume();
        }
    }

    private void processMetadata(MetadataPacket packet) {
        if (metadataPacket != null || inTxState != InTxState.RECEIVING_DATA) {
            log.debug("TXID{} Ignoring metadata packet {}", cfdpTransactionId, packet);
            return;
        }
        if (packet.getChecksumType() != ChecksumType.MODULAR && packet.getChecksumType() != ChecksumType.NULL) {
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
        applyMetadata(packet);

        needsFinish = acknowledged || packet.closureRequested();
        this.acknowledged = packet.getHeader().isAcknowledged();

        sendInfoEvent(ETYPE_TRANSFER_META, "Received metadata: " + toEventMsg(packet));

        try {
            if (originatingTransactionId != null) {
                fileSaveHandler.processOriginatingTransactionId(originatingTransactionId);
            }
            fileSaveHandler.setObjectName(directoryListingResponse == null ? originalObjectName : null);

            try {
                var props = fileSaveHandler.getBucket().getPropertiesAsync().get();
                if (props.maxSize() - props.size() < fileSize) {
                    throw new IOException(
                            "File too big for bucket '" + getBucketName() + "' (" + fileSize + " bytes for "
                                    + (props.maxSize() - props.size()) + " available)");
                }
            } catch (ExecutionException e) {
                throw new IOException("Could not determine bucket properties", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            persistState();
            checkFileComplete();
        } catch (IOException e) {
            handleFault(ConditionCode.FILESTORE_REJECTION);
            log.warn(e.getMessage());
            pushError(e.getMessage());
        }
    }

    /**
     * sets the fields derived from the metadata packet
     */
    private void applyMetadata(MetadataPacket packet) {
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
        incomingDataFile.setSize(packet.getFileLength());
        originalObjectName = !packet.getDestinationFilename().isEmpty() ? packet.getDestinationFilename()
                : packet.getSourceFilename();
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
        persistState();

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
                return;
            }
        } else {
            if (fdp.getEndOffset() > maxFileSize) {
                String err = String.format(
                        "Received data file whose end offset %d is larger than the maximum file size %d",
                        fdp.getEndOffset(), maxFileSize);
                pushError(err);
                log.warn("TXID{} {}", cfdpTransactionId, err);
                handleFault(ConditionCode.FILE_SIZE_ERROR);
                return;
            }
        }

        try {
            incomingDataFile.addSegment(fdp);
        } catch (UncheckedIOException e) {
            log.error("TXID{} error storing file data", cfdpTransactionId, e);
            pushError("Error storing file data: " + e.getCause().getMessage());
            handleFault(ConditionCode.FILESTORE_REJECTION);
            return;
        }
        monitor.stateChanged(this);
        checkFileComplete();
    }

    private void onFileCompleted() {
        // verify checksum
        long expectedChecksum = eofPacket.getFileChecksum();
        if (metadataPacket.getChecksumType() == ChecksumType.NULL
                || expectedChecksum == incomingDataFile.getModularChecksum()) {
            log.info("TXID{} file completed, checksum OK", cfdpTransactionId);
            // save the file before changing the state such that if Yamcs is stopped in between,
            // the file is saved again after restart (instead of being lost)
            if (!saveFile(false, Collections.emptyList())) {
                return;
            }
            if (needsFinish) {
                finish(ConditionCode.NO_ERROR);
            } else {
                complete(ConditionCode.NO_ERROR);
            }
            sendInfoEvent(ETYPE_TRANSFER_FINISHED,
                    " downlink finished and saved in " + getBucketName() + "/" + getObjectName());
        } else {
            log.warn("TXID{} file checksum failure; EOF packet indicates {} while data received has {}",
                    cfdpTransactionId, expectedChecksum, incomingDataFile.getModularChecksum());
            if (!saveFile(true, Collections.emptyList())) {
                return;
            }
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
        } else {
            persistState();
        }

        sendFin();
    }

    private void sendFin() {
        cancelFinSendFuture();
        long delay = lastPduSentTime + sleepBetweenPdus - System.currentTimeMillis();
        if (delay <= 0) {
            doSendFin();
        } else {
            finSendFuture = executor.schedule(() -> {
                finSendFuture = null;
                if (inTxState == InTxState.FIN && !suspended) {
                    doSendFin();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelFinSendFuture() {
        if (finSendFuture != null) {
            finSendFuture.cancel(false);
            finSendFuture = null;
        }
    }

    private void doSendFin() {
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
        cancelFinSendFuture();
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
        suspended = true;
        changeState(TransferState.PAUSED);
        finTimer.cancel();
        cancelFinSendFuture();
        if (!acknowledged) {
            checkTimer.cancel();
        }
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
            // the inactivity timer has been cancelled when the transfer was suspended; restart it so the transfer
            // does not stay running indefinitely if nothing is received anymore.
            // If the file is complete, it is cancelled again when entering the FIN state.
            rescheduleInactivityTimer();
            if (incomingDataFile.isComplete() && eofReceived()) {
                onFileCompleted();
            } else if (acknowledged) {
                sendResumeNak();
            }
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
                    persistState();
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

    /**
     * Saves the file in the bucket.
     * <p>
     * This is the only place where the file is read from the temporary storage.
     *
     * @return false if the file could not be saved (and the fault has been handled)
     */
    /**
     * Adds to the object metadata where the file comes from, such that an object in the bucket can be traced back to
     * the CFDP transaction that produced it.
     */
    private void addSourceMetadata(Map<String, String> metadata) {
        metadata.put("cfdpTransactionId", cfdpTransactionId.toString());
        metadata.put("sourceEntityId", Long.toString(directiveHeader.getSourceId()));
        metadata.put("destinationEntityId", Long.toString(directiveHeader.getDestinationId()));
        if (metadataPacket != null && !metadataPacket.getSourceFilename().isEmpty()) {
            metadata.put("sourceFilename", metadataPacket.getSourceFilename());
        }
        if (originatingTransactionId != null) {
            metadata.put("originatingTransactionId", originatingTransactionId.toString());
        }
        metadata.put("transferClass", acknowledged ? "2" : "1");
        metadata.put("transferStart", TimeEncoding.toString(startTime));
    }

    private boolean saveFile(boolean checksumError, List<SegmentRequest> missingSegments) {
        if (directoryListingResponse != null) {
            log.debug("TXID{} Ignoring save action for Directory Listing Response", cfdpTransactionId);
            return true;
        }

        Map<String, String> metadata = new HashMap<>();
        if (!missingSegments.isEmpty()) {
            metadata.put("missingSegments", missingSegments.toString());
        }
        if (checksumError) {
            metadata.put("checksumError", "true");
        }
        addSourceMetadata(metadata);

        try {
            fileSaveHandler.saveFile(incomingDataFile, metadata, originatingTransactionId);
            return true;
        } catch (RuntimeException e) {
            log.error("TXID{} failed to save file", cfdpTransactionId, e);
            pushError("Failed to save file: " + e.getMessage());
            handleFault(ConditionCode.FILESTORE_REJECTION);
            return false;
        }
    }

    @Override
    protected void sendPacket(CfdpPacket packet) {
        super.sendPacket(packet);
        lastPduSentTime = System.currentTimeMillis();
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


    // ---------------- persistence ----------------

    @Override
    protected void changeState(TransferState newState) {
        super.changeState(newState);
        persistState();
    }

    @Override
    protected void failTransfer(String failureReason) {
        inTxState = InTxState.COMPLETED;
        cancelTimers();
        super.failTransfer(failureReason);
    }

    private void cancelTimers() {
        cancelInactivityTimer();
        finTimer.cancel();
        cancelFinSendFuture();
        if (checkTimer != null) {
            checkTimer.cancel();
        }
    }

    /**
     * Save the state (after flushing the data to disk). The writing is done asynchronously by the store. Does nothing
     * if the transfer is not persisted.
     */
    private void persistState() {
        if (store == null || storageReleased) {
            return;
        }
        store.saveStateAsync(id, incomingDataFile, snapshot());
        stateDirty = false;
    }

    @Override
    protected void periodicSync() {
        if (store == null || storageReleased || inTxState == InTxState.COMPLETED) {
            return;
        }
        if (stateDirty) {
            persistState();
        } else {
            store.syncAsync(incomingDataFile);
        }
    }

    @Override
    protected boolean persistOnShutdown() {
        if (store == null || storageReleased || inTxState == InTxState.COMPLETED) {
            return false;
        }
        cancelTimers();
        try {
            store.saveState(id, incomingDataFile, snapshot());
        } catch (IOException e) {
            log.error("TXID{} failed to save the transfer state", cfdpTransactionId, e);
        }
        incomingDataFile.close();
        log.info("TXID{} transfer state saved ({}/{} bytes received)", cfdpTransactionId,
                incomingDataFile.getReceivedSize(), incomingDataFile.getSize());
        return true;
    }

    @Override
    protected void releaseStorage() {
        if (storageReleased) {
            return;
        }
        storageReleased = true;
        if (store != null) {
            store.deleteAsync(id, incomingDataFile);
        } else {
            incomingDataFile.delete();
        }
    }

    TransferSnapshot snapshot() {
        var inc = IncomingTransferSnapshot.newBuilder()
                .setInTxState(IncomingTransferSnapshot.InTxState.valueOf(inTxState.name()))
                .setSuspended(suspended)
                .setNeedsFinish(needsFinish)
                .setNakCount(nakCount)
                .setLastNakDataSize(lastNakDataSize);
        if (cancelUponResume != null) {
            inc.setCancelUponResume(cancelUponResume.getCode());
        }
        if (metadataPacket != null) {
            inc.setMetadataPdu(ByteString.copyFrom(metadataPacket.toByteArray()));
        }
        if (eofPacket != null) {
            inc.setEofPdu(ByteString.copyFrom(eofPacket.toByteArray()));
        }
        if (finPacket != null) {
            inc.setFinishedPdu(ByteString.copyFrom(finPacket.toByteArray()));
        }
        if (fileSaveHandler.getBucketName() != null) {
            inc.setBucket(fileSaveHandler.getBucketName());
        }
        if (fileSaveHandler.getObjectName() != null) {
            inc.setObjectName(fileSaveHandler.getObjectName());
        }
        var hdr = CfdpHeaderFields.newBuilder()
                .setEntityIdLength(directiveHeader.getEntityIdLength())
                .setSequenceNumberLength(directiveHeader.getSequenceNumberLength())
                .setSourceId(directiveHeader.getSourceId())
                .setDestinationId(directiveHeader.getDestinationId())
                .setSequenceNumber(directiveHeader.getSequenceNumber())
                .setAcknowledged(directiveHeader.isAcknowledged())
                .setLargeFile(directiveHeader.isLargeFile());

        return snapshotBuilder(SnapshotDirection.INCOMING, hdr.build()).setIncoming(inc).build();
    }

    /**
     * Rebuilds the CFDP header of the transaction (as seen in the PDUs received from the sender) from a snapshot.
     */
    static CfdpHeader headerFromSnapshot(TransferSnapshot snapshot) {
        CfdpHeaderFields h = snapshot.getHeader();
        return new CfdpHeader(false, false, h.getAcknowledged(), false, h.getEntityIdLength(),
                h.getSequenceNumberLength(), h.getSourceId(), h.getDestinationId(), h.getSequenceNumber());
    }

    /**
     * Restores a transfer from a snapshot. The data file is opened and verified.
     * <p>
     * The transfer is not active (no timer is running, no PDU sent) until {@link #resumeAfterRestore()} is called.
     *
     * @param bucket
     *            the bucket where the file will be saved, as resolved before the restart; can be null if the metadata
     *            has not been received.
     * @throws IOException
     *             if the data file cannot be opened
     * @throws IllegalArgumentException
     *             if the snapshot is invalid
     */
    static CfdpIncomingTransfer restore(String yamcsInstance, TransferSnapshot snapshot,
            ScheduledThreadPoolExecutor executor, YConfiguration config, Stream cfdpOut,
            FileSaveHandler fileSaveHandler, Bucket bucket, EventProducer eventProducer, TransferMonitor monitor,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions, CfdpTransferStore store)
            throws IOException {
        if (snapshot.getDirection() != SnapshotDirection.INCOMING || !snapshot.hasIncoming()
                || !snapshot.hasHeader()) {
            throw new IllegalArgumentException("not an incoming transfer snapshot");
        }
        var inc = snapshot.getIncoming();
        MetadataPacket metadata = (MetadataPacket) decodePdu(inc.hasMetadataPdu(), inc.getMetadataPdu(),
                "metadata");
        EofPacket eof = (EofPacket) decodePdu(inc.hasEofPdu(), inc.getEofPdu(), "EOF");
        FinishedPacket fin = (FinishedPacket) decodePdu(inc.hasFinishedPdu(), inc.getFinishedPdu(), "Finished");

        long maxFileSize = config.getLong("maxFileSize", 100 * 1024 * 1024l);
        long maxEnd = metadata != null ? metadata.getFileLength() : maxFileSize;

        AbstractDataFile dataFile = store.openDataFile(snapshot.getId(), maxEnd);
        CfdpIncomingTransfer trsf;
        try {
            trsf = new CfdpIncomingTransfer(yamcsInstance, snapshot.getId(), snapshot.getCreationTime(), executor,
                    config, headerFromSnapshot(snapshot), cfdpOut, fileSaveHandler, eventProducer, monitor,
                    faultHandlerActions, store, dataFile);
        } catch (RuntimeException e) {
            dataFile.close();
            throw e;
        }
        // the constructor starts the inactivity timer; it will be restarted if needed in resumeAfterRestore
        trsf.cancelInactivityTimer();
        trsf.restoreCommon(snapshot);
        if (metadata != null) {
            trsf.applyMetadata(metadata);
        }
        trsf.eofPacket = eof;
        trsf.finPacket = fin;
        trsf.needsFinish = inc.getNeedsFinish();
        trsf.suspended = inc.getSuspended();
        trsf.inTxState = InTxState.valueOf(inc.getInTxState().name());
        if (inc.hasCancelUponResume()) {
            trsf.cancelUponResume = ConditionCode.readConditionCode((byte) (inc.getCancelUponResume() << 4));
        }
        trsf.nakCount = inc.getNakCount();
        trsf.lastNakDataSize = inc.getLastNakDataSize();
        if (inc.hasObjectName() || bucket != null) {
            fileSaveHandler.restore(bucket, inc.hasObjectName() ? inc.getObjectName() : null);
        }
        if (trsf.inTxState == InTxState.FIN && fin == null) {
            dataFile.close();
            throw new IllegalArgumentException("FIN state without Finished PDU");
        }
        if (trsf.inTxState == InTxState.COMPLETED) {
            dataFile.close();
            throw new IllegalArgumentException("transfer already completed");
        }
        if (dataFile instanceof DiskDataFile ddf && ddf.getDroppedRecords() > 0) {
            trsf.log.warn("TXID{} {} bytes of data could not be verified after restart and are considered missing",
                    trsf.cfdpTransactionId, ddf.getDroppedBytes());
        }
        return trsf;
    }

    private static CfdpPacket decodePdu(boolean present, ByteString bs, String what) {
        if (!present) {
            return null;
        }
        try {
            return CfdpPacket.getCFDPPacket(ByteBuffer.wrap(bs.toByteArray()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cannot decode the saved " + what + " PDU: " + e.getMessage(), e);
        }
    }

    /**
     * Restarts the timers and sends the PDUs needed to continue a restored transfer. Has to be called on the executor
     * thread.
     */
    void resumeAfterRestore() {
        log.info("TXID{} restored in state {}/{} {}; {}/{} bytes received", cfdpTransactionId, inTxState, state,
                suspended ? "(suspended)" : "", incomingDataFile.getReceivedSize(), incomingDataFile.getSize());
        // save the state (also confirms the journal compaction)
        persistState();
        if (suspended) {
            return;
        }
        switch (inTxState) {
        case RECEIVING_DATA:
            rescheduleInactivityTimer();
            if (eofReceived() && metadataReceived()) {
                if (eofPacket.getConditionCode() != ConditionCode.NO_ERROR) {
                    // the EOF has been processed before the restart
                    break;
                }
                checkFileComplete();
                if (!acknowledged && inTxState == InTxState.RECEIVING_DATA) {
                    checkTimer.start(this::checkFileComplete, () -> {
                        log.warn("TXID{} check limit reached", cfdpTransactionId);
                        handleFault(ConditionCode.CHECK_LIMIT_REACHED);
                    });
                }
            } else if (acknowledged) {
                sendOrScheduleNak();
            }
            break;
        case FIN:
            sendFin();
            break;
        case COMPLETED:
            break;
        }
    }

    AbstractDataFile getDataFile() {
        return incomingDataFile;
    }
}
