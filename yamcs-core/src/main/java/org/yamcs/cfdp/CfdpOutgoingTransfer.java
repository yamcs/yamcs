package org.yamcs.cfdp;

import static org.yamcs.cfdp.CfdpService.ETYPE_EOF_LIMIT_REACHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_FINISHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_META;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_RESUMED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_SUSPENDED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import org.yamcs.cfdp.pdu.KeepAlivePacket;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.cfdp.pdu.TLV;
import org.yamcs.events.EventProducer;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public class CfdpOutgoingTransfer extends OngoingCfdpTransfer {

    private enum OutTxState {
        /**
         * Initial state. Going to SENDING_DATA in the first sendPdu step.
         */
        START,
        /**
         * Sending data and EOF. Going to FINISHED as soon as the Finished PDU is received.
         */
        SENDING_DATA,
        /**
         * Sending CANCEL EOF Going to COMPLETED as soon as the CANCEL EOF ACK is received
         */
        CANCELING,
        /**
         * End state. Still sending FINISHED ACK in return of Finished PDUs.
         */
        COMPLETED,
    }

    private final boolean withCrc = false; // no CRCs are used
    private final CfdpHeader directiveHeader, dataHeader;
    final Timer eofTimer;
    private final int entityIdLength;
    private final int seqNrSize;
    private Bucket bucket;
    private final int maxDataSize;
    private final int sleepBetweenPdus;
    private final boolean closureRequested;
    private final List<FileDataPacket> sentFileDataPackets = new ArrayList<>();
    private Queue<FileDataPacket> toResend;

    private OutTxState outTxState;
    private long transferred;

    private long offset = 0;
    private long end = 0;

    private boolean suspended = false;
    private final ChecksumType checksumType = ChecksumType.MODULAR;

    private PutRequest request;
    private ScheduledFuture<?> pduSendingSchedule;
    FinishedPacket finishedPacket;

    boolean resendMetadata = false;
    boolean eofSent = false;
    boolean eofAckReceived = false;

    EofPacket eofPacket;
    MetadataPacket metadata;
    ConditionCode reasonForCancellation;

    /**
     * Create a new CFDP outgoing (uplink) transfer.
     * <p>
     * The transfer has to be started with the {@link #start()} method.
     *
     * @param yamcsInstance
     *            - yamcsInstance where this transfer is running. It is used for log configuration and to get the time
     *            service.
     * @param initiatorEntityId
     * @param id
     *            - unique identifier. The least significant number of bits (according to the CFDP sequence length) of
     *            this id will be used to make the CFDP transaction id.
     * @param creationTime
     *            - time when the transaction has been created
     * @param executor
     *            - the CFDP state machine is serialized in this executor. It is also used to schedule timeouts.
     * @param request
     *            - the request containing the file to be sent.
     * @param cfdpOut
     *            - the stream where the outgoing PDUs are placed.
     * @param config
     *            - the configuration of various settings (see yamcs manual)
     * @param bucket
     *            - bucket from/to which the file should be
     * @param customPduSize
     *            - if not null, size to overwrite the config maxPduSize
     * @param customPduDelay
     *            - if not null, delay to overwrite the config sleepBetweenPdus
     * @param eventProducer
     *            - used to send events when important things happen
     * @param monitor
     *            - will be notified when transaction status changes
     * @param faultHandlerActions
     *            - can be used to change behaviour in case of timeouts or failures. Can be null, in which case the
     *            default behaviour to cancel the transaction will be used.
     */
    public CfdpOutgoingTransfer(String yamcsInstance, long initiatorEntityId, long id, long creationTime,
            ScheduledThreadPoolExecutor executor,
            PutRequest request, Stream cfdpOut, YConfiguration config, Bucket bucket,
            Integer customPduSize, Integer customPduDelay,
            EventProducer eventProducer,
            TransferMonitor monitor, Map<ConditionCode, FaultHandlingAction> faultHandlerActions) {
        super(yamcsInstance, id, creationTime,
                executor, config, makeTransactionId(initiatorEntityId, config, id),
                request.getDestinationCfdpEntityId(), cfdpOut,
                eventProducer, monitor, faultHandlerActions);
        this.request = request;
        this.metadata = request.getMetadata();
        transferType = getTransferType(metadata);
        this.bucket = bucket;
        entityIdLength = config.getInt("entityIdLength");
        seqNrSize = config.getInt("sequenceNrLength");
        int maxPduSize = customPduSize != null && customPduSize > 0 ? customPduSize : config.getInt("maxPduSize", 512);
        maxDataSize = maxPduSize - 4 - 2 * entityIdLength - seqNrSize - 4;
        long eofAckTimeout = config.getInt("eofAckTimeout", 10000);
        int eofAckLimit = config.getInt("eofAckLimit", 5);

        eofTimer = new Timer(executor, eofAckLimit, eofAckTimeout);
        acknowledged = request.isAcknowledged();

        outTxState = OutTxState.START;
        this.sleepBetweenPdus = customPduDelay != null && customPduDelay > 0 ? customPduDelay
                : config.getInt("sleepBetweenPdus", 500);
        this.closureRequested = request.isClosureRequested();

        if (request.getHeader() != null) {
            directiveHeader = request.getHeader().copy(true);
            dataHeader = request.getHeader().copy(false);
        } else {
            // create header for all file directive PDUs
            directiveHeader = new CfdpHeader(
                    true, // it's a file directive
                    false, // it's sent towards the receiver
                    acknowledged,
                    withCrc, // no CRC
                    entityIdLength,
                    seqNrSize,
                    cfdpTransactionId.getInitiatorEntity(), // my Entity Id
                    request.getDestinationCfdpEntityId(), // the id of the target
                    cfdpTransactionId.getSequenceNumber());

            dataHeader = new CfdpHeader(
                    false, // it's file data
                    false, // it's sent towards the receiver
                    acknowledged,
                    withCrc, // no CRC
                    entityIdLength,
                    seqNrSize,
                    getTransactionId().getInitiatorEntity(), // my Entity Id
                    request.getDestinationCfdpEntityId(), // the id of the target
                    this.cfdpTransactionId.getSequenceNumber());
        }
    }

    private static CfdpTransactionId makeTransactionId(long sourceId, YConfiguration config, long id) {
        int seqNrSize = config.getInt("sequenceNrLength");
        long seqNum = id & ((1l << seqNrSize * 8) - 1);

        return new CfdpTransactionId(sourceId, seqNum);
    }

    /**
     * Start the transfer
     */
    public void start() {
        pduSendingSchedule = executor.scheduleAtFixedRate(this::sendPDU, 0, sleepBetweenPdus, TimeUnit.MILLISECONDS);
    }

    private void sendPDU() {
        if (suspended) {
            return;
        }

        switch (outTxState) {
        case START:
            if (metadata == null) {
                metadata = getMetadataPacket();
                transferType = getTransferType(metadata);
            }
            sendInfoEvent(ETYPE_TRANSFER_META, "Sending metadata: " + toEventMsg(metadata));
            sendPacket(metadata);
            this.outTxState = OutTxState.SENDING_DATA;
            offset = 0; // first file data packet starts at the start of the data
            end = Math.min(maxDataSize, request.getFileLength());
            monitor.stateChanged(this);
            break;
        case SENDING_DATA:
            if (resendMetadata) {
                sendPacket(metadata);
                resendMetadata = false;
            } else {
                if (offset == request.getFileLength()) {
                    if (toResend != null && !toResend.isEmpty()) {
                        FileDataPacket fdp = toResend.poll();
                        sendPacket(fdp);
                    } else if (!eofSent) {
                        sendEof(ConditionCode.NO_ERROR);
                    }
                } else {
                    end = Math.min(offset + maxDataSize, request.getFileLength());
                    FileDataPacket nextPacket = getNextFileDataPacket();
                    sentFileDataPackets.add(nextPacket);
                    sendPacket(nextPacket);
                    transferred += (end - offset);
                    offset = end;
                }
            }
            monitor.stateChanged(this);
            break;
        case COMPLETED:
            pduSendingSchedule.cancel(true);
            cancelInactivityTimer();
            break;
        default:
            throw new IllegalStateException("unknown/illegal state");
        }
    }

    private void sendEof(ConditionCode code) {
        // remember the EOF sent, we need to resend it in case of suspend/resume
        eofPacket = getEofPacket(code);
        sendEof();
    }

    private void sendEof() {
        sendPacket(eofPacket);
        eofSent = true;

        if (!acknowledged && !metadata.closureRequested()) {
            complete(ConditionCode.NO_ERROR);
        } else {
            eofTimer.start(() -> sendPacket(eofPacket),
                    () -> {
                        sendWarnEvent(ETYPE_EOF_LIMIT_REACHED,
                                "Resend attempts (" + eofTimer.maxNumAttempts + ") of EOF reached");
                        handleFault(ConditionCode.ACK_LIMIT_REACHED);
                    });
        }
    }

    @Override
    public void processPacket(CfdpPacket packet) {
        executor.submit(() -> doProcessPacket(packet));
    }

    private void doProcessPacket(CfdpPacket packet) {
        if (log.isDebugEnabled()) {
            log.debug("TXID{} state:{}, received PDU: {}", cfdpTransactionId, state, packet);
            log.trace("{}", StringConverter.arrayToHexString(packet.toByteArray(), true));
        }
        if (state == TransferState.COMPLETED || state == TransferState.FAILED) {
            if (packet instanceof FinishedPacket) {
                sendPacket(getAckPacket(((FinishedPacket) packet).getConditionCode()));
            } else {
                log.info("Ignoring PDU {} for finished transaction {}", packet, cfdpTransactionId);
            }
            return;
        }
        if (eofAckReceived) {
            rescheduleInactivityTimer();
        }
        if (packet instanceof AckPacket) {
            processAckPacket((AckPacket) packet);
        } else if (packet instanceof FinishedPacket) {
            processFinishedPacket((FinishedPacket) packet);
        } else if (packet instanceof NakPacket) {
            toResend = new LinkedList<>();
            for (SegmentRequest segment : ((NakPacket) packet).getSegmentRequests()) {
                if (segment.isMetadata()) {
                    resendMetadata = true;
                } else {
                    toResend.addAll(sentFileDataPackets.stream()
                            .filter(x -> segment.isInRange(x.getOffset()))
                            .collect(Collectors.toList()));
                }
            }
        } else if (packet instanceof KeepAlivePacket) {
            log.info("TXID{} Ignoring Keep Alive PDU: {}", cfdpTransactionId, packet); // Handling not implemented
        } else {
            log.warn("TXID{} unexpected packet {} ", cfdpTransactionId, packet);
        }
    }

    private void processAckPacket(AckPacket ackPacket) {
        if (ackPacket.getDirectiveCode() != FileDirectiveCode.EOF) {
            log.info("TXID{} received bogus non EOF ACK packet: {}", cfdpTransactionId, ackPacket);
        }

        if (!eofSent) {
            log.info("TXID{} received unexpected ACK packet (EOF not sent): {}", cfdpTransactionId, ackPacket);
            return;
        }
        eofTimer.cancel();

        if (outTxState == OutTxState.CANCELING) {
            complete(reasonForCancellation);
        }
    }

    private void processFinishedPacket(FinishedPacket finishedPacket) {
        eofTimer.cancel();

        sendPacket(getAckPacket(finishedPacket.getConditionCode()));

        if (outTxState == OutTxState.COMPLETED) {
            return;
        }

        // depending on the conditioncode, the transfer was a success or a failure
        if (finishedPacket.getConditionCode() != ConditionCode.NO_ERROR) {
            complete(finishedPacket.getConditionCode());
        } else {
            if (eofSent) {
                complete(ConditionCode.NO_ERROR);
            } else {
                log.warn("TXID{} received Finished PDU before sending the EOF: {}", cfdpTransactionId,
                        finishedPacket);
            }
        }
    }

    /**
     * The inactivity timer is active after the EOF ACK has been received
     */
    @Override
    protected void onInactivityTimerExpiration() {
        log.warn("TXID{} Inactivity timeout while in {} state; transaction failed", cfdpTransactionId,
                outTxState);
        handleFault(ConditionCode.INACTIVITY_DETECTED);
    }

    @Override
    protected void suspend() {
        if (outTxState == OutTxState.COMPLETED) {
            log.info("TXID{} transfer finished, suspend ignored", cfdpTransactionId);
            return;
        }

        sendInfoEvent(ETYPE_TRANSFER_SUSPENDED, "transfer suspended");
        log.info("TXID{} suspending transfer", cfdpTransactionId);

        eofTimer.cancel();
        pduSendingSchedule.cancel(true);
        cancelInactivityTimer();

        suspended = true;
        changeState(TransferState.PAUSED);
    }

    @Override
    protected void resume() {
        if (!suspended) {
            log.info("TXID{} resume called while not suspended, ignoring", cfdpTransactionId);
            return;
        }
        if (outTxState == OutTxState.COMPLETED) {
            // it is possible the transfer has finished while being suspended
            log.info("TXID{} transfer finished, suspend ignored", cfdpTransactionId);
            return;
        }
        log.info("TXID{} resuming transfer", cfdpTransactionId);
        sendInfoEvent(ETYPE_TRANSFER_RESUMED, "transfer resumed");
        pduSendingSchedule = executor.scheduleAtFixedRate(this::sendPDU, 0, sleepBetweenPdus, TimeUnit.MILLISECONDS);
        if (expectingAck()) {
            sendEof();
        }
        if (outTxState == OutTxState.SENDING_DATA && eofAckReceived) {
            rescheduleInactivityTimer();
        }

        changeState(TransferState.RUNNING);
        suspended = false;
    }

    private boolean expectingAck() {
        return (outTxState == OutTxState.SENDING_DATA
                || outTxState == OutTxState.CANCELING)
                && eofSent
                && !eofAckReceived;
    }

    public OutTxState getCfdpState() {
        return this.outTxState;
    }

    private void complete(ConditionCode conditionCode) {
        if (outTxState == OutTxState.COMPLETED) {
            return;
        }
        outTxState = OutTxState.COMPLETED;

        long duration = (System.currentTimeMillis() - wallclockStartTime) / 1000;

        String eventMessageSuffix;
        if (metadata.getFileLength() > 0 || directiveHeader.isLargeFile()) {
            eventMessageSuffix = request.getSourceFileName() + " -> " + request.getDestinationFileName();
        } else {
            eventMessageSuffix = "Fileless transfer (metadata options: \n"
                    + metadata.getOptions().stream().map(TLV::toString).collect(Collectors.joining(",\n")) + "\n)";
        }

        if (conditionCode == ConditionCode.NO_ERROR) {
            changeState(TransferState.COMPLETED);
            sendInfoEvent(ETYPE_TRANSFER_FINISHED,
                    "transfer finished successfully in " + duration + " seconds: "
                            + eventMessageSuffix);
        } else {
            failTransfer(conditionCode.toString());
            sendWarnEvent(ETYPE_TRANSFER_FINISHED,
                    "transfer finished with error in " + duration + " seconds: "
                            + eventMessageSuffix + ", error: " + finishedPacket.getConditionCode());
        }
    }

    @Override
    protected void cancel(ConditionCode conditionCode) {
        log.debug("TXID{} Cancelling with code {}", cfdpTransactionId, conditionCode);
        switch (outTxState) {
        case START:
        case SENDING_DATA:
            reasonForCancellation = conditionCode;
            suspended = false; // wake up if sleeping
            outTxState = OutTxState.CANCELING;
            changeState(TransferState.CANCELLING);
            sendEof(conditionCode);
            break;
        case CANCELING:
        case COMPLETED:
            break;
        }
    }

    private void handleFault(ConditionCode conditionCode) {
        log.debug("TXID{} Handling fault {}", cfdpTransactionId, conditionCode);

        if (outTxState == OutTxState.CANCELING) {
            complete(conditionCode);
        } else {
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
            }
        }
    }

    @Override
    public TransferDirection getDirection() {
        return TransferDirection.UPLOAD;
    }

    @Override
    public long getTotalSize() {
        return this.request.getFileLength();
    }

    @Override
    public String getBucketName() {
        return bucket != null ? bucket.getName() : null;
    }

    @Override
    public String getObjectName() {
        return request.getSourceFileName();
    }

    @Override
    public String getRemotePath() {
        return request.getDestinationFileName();
    }

    @Override
    public long getTransferredSize() {
        return this.transferred;
    }

    private EofPacket getEofPacket(ConditionCode code) {
        long checksum;
        long filesize;
        TLV tlv;

        if (code == ConditionCode.NO_ERROR) {
            checksum = request.getChecksum();
            filesize = request.getFileLength();
            tlv = null;
        } else {
            filesize = getTransferredSize();
            checksum = ChecksumCalculator.calculateChecksum(request.getFileData(), 0l, filesize);
            tlv = TLV.getEntityIdTLV(cfdpTransactionId.getInitiatorEntity(), entityIdLength);
        }

        return new EofPacket(code, checksum, filesize, tlv, directiveHeader);
    }

    private MetadataPacket getMetadataPacket() {
        return new MetadataPacket(
                closureRequested, checksumType,
                request.getFileLength(),
                request.getSourceFileName(),
                request.getDestinationFileName(),
                null, directiveHeader);
    }

    private FileDataPacket getNextFileDataPacket() {
        return new FileDataPacket(Arrays.copyOfRange(request.getFileData(), (int) offset, (int) end),
                offset, dataHeader);
    }

    private AckPacket getAckPacket(ConditionCode code) {
        return new AckPacket(
                FileDirectiveCode.FINISHED,
                FileDirectiveSubtypeCode.FINISHED_BY_END_SYSTEM,
                code,
                TransactionStatus.TERMINATED,
                directiveHeader);
    }
}
