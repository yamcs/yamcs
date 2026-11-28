package org.yamcs.cfdp;

import static org.yamcs.cfdp.CfdpService.ETYPE_EOF_LIMIT_REACHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_FINISHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_META;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_RESUMED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_SUSPENDED;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.buckets.Bucket;
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
import org.yamcs.cfdp.protobuf.CfdpHeaderFields;
import org.yamcs.cfdp.protobuf.FileSegment;
import org.yamcs.cfdp.protobuf.OutgoingTransferSnapshot;
import org.yamcs.cfdp.protobuf.SnapshotDirection;
import org.yamcs.cfdp.protobuf.TransferSnapshot;
import org.yamcs.events.EventProducer;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.Stream;

import com.google.protobuf.ByteString;

/**
 * CFDP sender (uplink).
 * <p>
 * The file to be sent is not kept in memory: when the transfer starts, the object is read from the bucket and copied
 * into the transfer directory (see {@link #fetchSource()}); the File Data PDUs are then built with positional reads
 * from that copy.
 * <p>
 * If a {@link CfdpTransferStore} is given and persistence is enabled, the state of the transfer is saved on each state
 * transition, on NAK reception and periodically, such that the transfer can be continued after a restart (see
 * {@link #restore}).
 */
public class CfdpOutgoingTransfer extends OngoingCfdpTransfer {

    enum OutTxState {
        /**
         * Initial state. The data file is prepared, then going to SENDING_DATA when sending the Metadata PDU.
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

    // the file size and offsets are encoded on 4 bytes (large files not supported)
    static final long MAX_FILE_SIZE = 0xFFFF_FFFFL;
    private final boolean withCrc = false; // no CRCs are used
    private final CfdpHeader directiveHeader, dataHeader;
    final Timer eofTimer;
    private final int entityIdLength;
    private final int seqNrSize;
    private Bucket bucket;
    private final int maxDataSize;
    private final int sleepBetweenPdus;
    private final boolean closureRequested;
    private final MissingSegments toResend = new MissingSegments();
    private final Integer customPduSize;
    private final Integer customPduDelay;
    private final boolean autoResume;

    private OutTxState outTxState;
    private long transferred;

    private long offset = 0;
    private long end = 0;

    private boolean suspended = false;
    private final ChecksumType checksumType;

    private PutRequest request;
    private ScheduledFuture<?> pduSendingSchedule;

    boolean resendMetadata = false;
    boolean eofSent = false;
    boolean eofAckReceived = false;

    EofPacket eofPacket;
    MetadataPacket metadata;
    ConditionCode reasonForCancellation;

    // ---- file data ----
    // true if the request is for a file (as opposed to a metadata only transfer)
    private final boolean fileTransfer;
    // the data to be sent; null until prepared (or always null for the metadata only transfers)
    private OutgoingFileSource source;
    private long fileLength;
    // modular checksum of the whole file
    private long fileChecksum;
    // true while the file is being read from the bucket
    private boolean preparing;
    // reused for all the full size File Data PDUs (the PDU is serialized when sent)
    private byte[] pduBuf;

    // ---- persistence ----
    // where the data file is stored; if null, the data is kept in memory
    private final CfdpTransferStore store;
    // if true, the state is saved in the store
    private final boolean persist;
    private boolean stateDirty;
    private boolean storageReleased;
    // set when the service stops; the transfer should not do anything anymore
    private boolean stopped;

    /**
     * Create a new CFDP outgoing (uplink) transfer whose data is kept in memory and which is not persisted.
     * <p>
     * The transfer has to be started with the {@link #start()} method.
     *
     * @see #CfdpOutgoingTransfer(String, long, long, long, ScheduledThreadPoolExecutor, PutRequest, Stream,
     *      YConfiguration, Bucket, Integer, Integer, EventProducer, TransferMonitor, Map, CfdpTransferStore, boolean)
     */
    public CfdpOutgoingTransfer(String yamcsInstance, long initiatorEntityId, long id, long creationTime,
            ScheduledThreadPoolExecutor executor,
            PutRequest request, Stream cfdpOut, YConfiguration config, Bucket bucket,
            Integer customPduSize, Integer customPduDelay,
            EventProducer eventProducer,
            TransferMonitor monitor, Map<ConditionCode, FaultHandlingAction> faultHandlerActions) {
        this(yamcsInstance, initiatorEntityId, id, creationTime, executor, request, cfdpOut, config, bucket,
                customPduSize, customPduDelay, eventProducer, monitor, faultHandlerActions, null, false);
    }

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
     *            - the request referencing the file to be sent.
     * @param cfdpOut
     *            - the stream where the outgoing PDUs are placed.
     * @param config
     *            - the configuration of various settings (see yamcs manual)
     * @param bucket
     *            - bucket from which the file is read
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
     * @param store
     *            - if not null, the file to be sent is copied in this store when the transfer starts; if null, the
     *            file is kept in memory.
     * @param persist
     *            - if true (and the store is not null), the state of the transfer is saved in the store such that it
     *            can be restored after a restart.
     */
    public CfdpOutgoingTransfer(String yamcsInstance, long initiatorEntityId, long id, long creationTime,
            ScheduledThreadPoolExecutor executor,
            PutRequest request, Stream cfdpOut, YConfiguration config, Bucket bucket,
            Integer customPduSize, Integer customPduDelay,
            EventProducer eventProducer,
            TransferMonitor monitor, Map<ConditionCode, FaultHandlingAction> faultHandlerActions,
            CfdpTransferStore store, boolean persist) {
        super(yamcsInstance, id, creationTime,
                executor, config, makeTransactionId(initiatorEntityId, config, id),
                request.getDestinationCfdpEntityId(), cfdpOut,
                eventProducer, monitor, faultHandlerActions == null ? Map.of() : faultHandlerActions);
        this.request = request;
        this.metadata = request.getMetadata();
        transferType = getTransferType(metadata);
        this.bucket = bucket;
        this.store = store;
        this.persist = persist && store != null;
        this.customPduSize = customPduSize;
        this.customPduDelay = customPduDelay;
        this.fileTransfer = request instanceof FilePutRequest;
        this.fileLength = request.getFileLength();
        entityIdLength = config.getInt("entityIdLength");
        seqNrSize = config.getInt("sequenceNrLength");
        int maxPduSize = customPduSize != null && customPduSize > 0 ? customPduSize : config.getInt("maxPduSize", 512);
        maxDataSize = maxPduSize - 4 - 2 * entityIdLength - seqNrSize - 4;
        long eofAckTimeout = config.getInt("eofAckTimeout", 10000);
        int eofAckLimit = config.getInt("eofAckLimit", 5);
        this.autoResume = config.getBoolean("autoResume", false);

        eofTimer = new Timer(executor, eofAckLimit, eofAckTimeout);
        acknowledged = request.isAcknowledged();

        outTxState = OutTxState.START;
        this.sleepBetweenPdus = customPduDelay != null && customPduDelay > 0 ? customPduDelay
                : config.getInt("sleepBetweenPdus", 500);
        this.closureRequested = request.isClosureRequested();
        this.checksumType = config.getEnum("checksumType", ChecksumType.class, ChecksumType.MODULAR);
        if (this.checksumType != ChecksumType.MODULAR && this.checksumType != ChecksumType.NULL) {
            throw new ConfigurationException(
                    "Checksump type " + this.checksumType + " not supported. Supported are: NULL and MODULAR");
        }

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
     * Uses the given data instead of reading the file from the bucket. Has to be called before {@link #start()}.
     */
    void setSource(OutgoingFileSource source) {
        this.source = source;
        this.fileLength = source.size();
        try {
            this.fileChecksum = source.checksum(fileLength);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Start the transfer
     */
    public void start() {
        executor.execute(() -> {
            persistState();
            startOrContinue();
        });
    }

    /**
     * Prepares the file if necessary and (re)starts sending the PDUs
     */
    private void startOrContinue() {
        if (fileTransfer && source == null) {
            if (!preparing) {
                prepareSource();
            }
        } else {
            startSending();
        }
    }

    private void startSending() {
        if (suspended || stopped || outTxState == OutTxState.COMPLETED || outTxState == OutTxState.CANCELING) {
            return;
        }
        if (pduSendingSchedule != null) {
            pduSendingSchedule.cancel(false);
        }
        pduSendingSchedule = executor.scheduleAtFixedRate(this::sendPDU, 0, sleepBetweenPdus, TimeUnit.MILLISECONDS);
    }

    private void stopSending() {
        if (pduSendingSchedule != null) {
            pduSendingSchedule.cancel(false);
            pduSendingSchedule = null;
        }
    }

    record PreparedSource(OutgoingFileSource source, long checksum) {
    }

    private void prepareSource() {
        preparing = true;
        CompletableFuture<PreparedSource> cf;
        try {
            cf = fetchSource();
        } catch (RuntimeException e) {
            cf = CompletableFuture.failedFuture(e);
        }
        cf.whenComplete((ps, err) -> {
            try {
                executor.execute(() -> onSourcePrepared(ps, err));
            } catch (RejectedExecutionException e) {
                // the service has been stopped
                if (ps != null) {
                    ps.source.close();
                }
            }
        });
    }

    /**
     * Reads the object to be sent from the bucket and makes it available as an {@link OutgoingFileSource}, computing
     * also its checksum.
     * <p>
     * This is the only place where the bucket object is read. Currently the object is read whole in memory and then
     * written to {@code <id>.data} in the transfer directory (or kept in memory if there is no store), after which the
     * array is discarded. When the buckets will support ranged reads, this can be replaced by a source reading
     * directly from the bucket (detecting if the object is modified during the transfer).
     */
    CompletableFuture<PreparedSource> fetchSource() {
        String objectName = request.getSourceFileName();
        if (bucket == null) {
            return CompletableFuture.failedFuture(new IOException("no bucket specified"));
        }
        return bucket.getObjectAsync(objectName).thenCompose(data -> {
            if (data == null) {
                throw new CompletionException(
                        new IOException("object '" + objectName + "' not found in bucket " + bucket.getName()));
            }
            if (store == null) {
                return CompletableFuture.completedFuture(new PreparedSource(OutgoingFileSource.ofBytes(data),
                        ModularChecksumCalculator.calculateChecksum(data)));
            }
            return store.supplyAsync(() -> {
                long checksum = ModularChecksumCalculator.calculateChecksum(data);
                return new PreparedSource(OutgoingFileSource.createFile(store.dataPath(id), data), checksum);
            });
        });
    }

    private void onSourcePrepared(PreparedSource ps, Throwable err) {
        preparing = false;
        if (stopped || storageReleased || outTxState != OutTxState.START) {
            // stopped or cancelled in the meanwhile
            if (ps != null) {
                ps.source.close();
            }
            return;
        }
        if (err != null) {
            Throwable c = (err instanceof CompletionException || err instanceof ExecutionException)
                    && err.getCause() != null ? err.getCause() : err;
            log.warn("TXID{} cannot read the file to be sent: {}", cfdpTransactionId, c.toString());
            outTxState = OutTxState.COMPLETED;
            String msg = "Cannot read the file to be sent: " + c.getMessage();
            sendWarnEvent(ETYPE_TRANSFER_FINISHED, "transfer failed: " + msg);
            failTransfer(msg);
            return;
        }
        if (ps.source.size() > MAX_FILE_SIZE) {
            ps.source.close();
            outTxState = OutTxState.COMPLETED;
            String msg = "File too large (" + ps.source.size() + " bytes); large files are not supported";
            sendWarnEvent(ETYPE_TRANSFER_FINISHED, "transfer failed: " + msg);
            failTransfer(msg);
            return;
        }
        source = ps.source;
        fileLength = source.size();
        fileChecksum = ps.checksum;
        log.debug("TXID{} file prepared: {} bytes, checksum {}", cfdpTransactionId, fileLength, fileChecksum);
        persistState();
        monitor.stateChanged(this);
        startSending();
    }

    // package private for the performance test
    void sendPDU() {
        if (suspended) {
            return;
        }

        switch (outTxState) {
        case START:
            if (fileTransfer && source == null) {
                // not yet prepared
                return;
            }
            if (metadata == null) {
                metadata = getMetadataPacket();
                transferType = getTransferType(metadata);
            }
            sendInfoEvent(ETYPE_TRANSFER_META, "Sending metadata: " + toEventMsg(metadata));
            sendPacket(metadata);
            this.outTxState = OutTxState.SENDING_DATA;
            offset = 0; // first file data packet starts at the start of the data
            end = Math.min(maxDataSize, fileLength);
            persistState();
            monitor.stateChanged(this);
            break;
        case SENDING_DATA:
            if (resendMetadata) {
                sendPacket(metadata);
                resendMetadata = false;
                stateDirty = true;
            } else {
                if (offset == fileLength) {
                    SegmentRequest segment = toResend.poll(maxDataSize);
                    if (segment != null) {
                        stateDirty = true;
                        if (!sendFileData(segment.getSegmentStart(), segment.getSegmentEnd())) {
                            return;
                        }
                    } else if (!eofSent) {
                        sendEof(ConditionCode.NO_ERROR);
                    }
                } else {
                    end = Math.min(offset + maxDataSize, fileLength);
                    if (!sendFileData(offset, end)) {
                        return;
                    }
                    transferred += (end - offset);
                    offset = end;
                    stateDirty = true;
                }
            }
            monitor.stateChanged(this);
            break;
        case CANCELING:
        case COMPLETED:
            stopSending();
            break;
        default:
            throw new IllegalStateException("unknown/illegal state");
        }
    }

    /**
     * Reads the data between start and end and sends it in a File Data PDU.
     *
     * @return false if the data could not be read (in which case the transfer is cancelled)
     */
    private boolean sendFileData(long start, long end) {
        int len = (int) (end - start);
        byte[] b;
        if (pduBuf != null && len == pduBuf.length) {
            b = pduBuf;
        } else if (len == maxDataSize) {
            pduBuf = new byte[maxDataSize];
            b = pduBuf;
        } else {
            b = new byte[len];
        }
        try {
            source.read(start, b, 0, len);
        } catch (IOException e) {
            log.error("TXID{} error reading the data to be sent", cfdpTransactionId, e);
            pushError("Error reading the data file: " + e.getMessage());
            stopSending();
            cancel(ConditionCode.FILESTORE_REJECTION);
            return false;
        }
        // the PDU is serialized by sendPacket, so the buffer can be reused afterwards
        sendPacket(new FileDataPacket(b, start, dataHeader));
        return true;
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
            persistState();
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
        if (stopped) {
            return;
        }
        if (state == TransferState.COMPLETED || state == TransferState.FAILED) {
            if (packet instanceof FinishedPacket) {
                sendPacket(getAckPacket(((FinishedPacket) packet).getConditionCode()));
            } else {
                log.info("Ignoring PDU {} for finished transaction {}", packet, cfdpTransactionId);
            }
            return;
        }
        if (eofAckReceived && !suspended) {
            rescheduleInactivityTimer();
        }
        if (packet instanceof AckPacket) {
            processAckPacket((AckPacket) packet);
        } else if (packet instanceof FinishedPacket) {
            processFinishedPacket((FinishedPacket) packet);
        } else if (packet instanceof NakPacket) {
            processNakPacket((NakPacket) packet);
        } else if (packet instanceof KeepAlivePacket) {
            log.info("TXID{} Ignoring Keep Alive PDU: {}", cfdpTransactionId, packet); // Handling not implemented
        } else {
            log.warn("TXID{} unexpected packet {} ", cfdpTransactionId, packet);
        }

        if (suspended && autoResume && outTxState != OutTxState.COMPLETED) {
            log.info("TXID{} PDU received from the receiver while suspended, resuming (autoResume)",
                    cfdpTransactionId);
            resume();
        }
    }

    /**
     * The NAK gives the complete list of missing segments within its scope, so it replaces any previously requested
     * segments within that scope.
     */
    private void processNakPacket(NakPacket nak) {
        toResend.remove(nak.getScopeStart(), nak.getScopeEnd());
        for (SegmentRequest segment : nak.getSegmentRequests()) {
            if (segment.isMetadata()) {
                resendMetadata = true;
            } else {
                toResend.add(Math.min(segment.getSegmentStart(), fileLength),
                        Math.min(segment.getSegmentEnd(), fileLength));
            }
        }
        log.debug("TXID{} segments to resend: {}", cfdpTransactionId, toResend);
        persistState();
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
        eofAckReceived = true;
        if (outTxState == OutTxState.SENDING_DATA && !suspended) {
            rescheduleInactivityTimer();
        }

        if (outTxState == OutTxState.CANCELING) {
            complete(reasonForCancellation);
        } else {
            persistState();
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
        log.warn("TXID{} Inactivity timeout while in {} state", cfdpTransactionId, outTxState);
        handleFault(ConditionCode.INACTIVITY_DETECTED);
    }

    @Override
    protected void suspend() {
        if (outTxState == OutTxState.COMPLETED) {
            log.info("TXID{} transfer finished, suspend ignored", cfdpTransactionId);
            return;
        }
        if (suspended) {
            log.info("TXID{} transfer already suspended", cfdpTransactionId);
            return;
        }

        sendInfoEvent(ETYPE_TRANSFER_SUSPENDED, "transfer suspended");
        log.info("TXID{} suspending transfer", cfdpTransactionId);

        eofTimer.cancel();
        stopSending();
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
        suspended = false;
        startOrContinue();
        if (expectingAck()) {
            sendEof();
        }
        if (outTxState == OutTxState.SENDING_DATA && eofAckReceived) {
            rescheduleInactivityTimer();
        }

        changeState(outTxState == OutTxState.CANCELING ? TransferState.CANCELLING : TransferState.RUNNING);
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
        stopSending();
        eofTimer.cancel();
        closeSource();

        long duration = (System.currentTimeMillis() - wallclockStartTime) / 1000;

        String eventMessageSuffix;
        if (fileTransfer || directiveHeader.isLargeFile()) {
            eventMessageSuffix = request.getSourceFileName() + " -> " + request.getDestinationFileName();
        } else if (metadata != null) {
            eventMessageSuffix = "Fileless transfer (metadata options: \n"
                    + metadata.getOptions().stream().map(TLV::toString).collect(Collectors.joining(",\n")) + "\n)";
        } else {
            eventMessageSuffix = "Fileless transfer";
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
                            + eventMessageSuffix + ", error: " + conditionCode);
        }
    }

    @Override
    protected void cancel(ConditionCode conditionCode) {
        log.debug("TXID{} Cancelling with code {}", cfdpTransactionId, conditionCode);
        switch (outTxState) {
        case START:
            // nothing has been sent to the receiver
            reasonForCancellation = conditionCode;
            suspended = false;
            complete(conditionCode);
            break;
        case SENDING_DATA:
            reasonForCancellation = conditionCode;
            suspended = false; // wake up if sleeping
            stopSending();
            outTxState = OutTxState.CANCELING;
            eofAckReceived = false; // the cancel EOF has to be acknowledged again
            eofPacket = getEofPacket(conditionCode);
            changeState(TransferState.CANCELLING);
            sendEof();
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
        return fileLength;
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
            filesize = fileLength;
            tlv = null;
            checksum = fileChecksum;
        } else {
            filesize = getTransferredSize();
            tlv = TLV.getEntityIdTLV(cfdpTransactionId.getInitiatorEntity(), entityIdLength);
            checksum = 0;
            if (source != null && filesize > 0 && checksumType != ChecksumType.NULL) {
                try {
                    checksum = source.checksum(filesize);
                } catch (IOException | RuntimeException e) {
                    log.warn("TXID{} cannot compute the checksum of the data sent: {}", cfdpTransactionId,
                            e.toString());
                }
            }
        }
        if (checksumType == ChecksumType.NULL) {
            checksum = 0;
        }

        return new EofPacket(code, checksum, filesize, tlv, directiveHeader);
    }

    private MetadataPacket getMetadataPacket() {
        return new MetadataPacket(
                closureRequested, checksumType,
                fileLength,
                request.getSourceFileName(),
                request.getDestinationFileName(),
                null, directiveHeader);
    }

    private AckPacket getAckPacket(ConditionCode code) {
        return new AckPacket(
                FileDirectiveCode.FINISHED,
                FileDirectiveSubtypeCode.FINISHED_BY_END_SYSTEM,
                code,
                TransactionStatus.TERMINATED,
                directiveHeader);
    }

    // ---------------- persistence ----------------

    @Override
    protected void changeState(TransferState newState) {
        super.changeState(newState);
        persistState();
    }

    @Override
    protected void failTransfer(String failureReason) {
        outTxState = OutTxState.COMPLETED;
        stopSending();
        eofTimer.cancel();
        cancelInactivityTimer();
        closeSource();
        super.failTransfer(failureReason);
    }

    private void closeSource() {
        if (source != null) {
            source.close();
        }
    }

    /**
     * Saves the state asynchronously. Does nothing if the transfer is not persisted.
     */
    private void persistState() {
        if (!persist || storageReleased || stopped) {
            return;
        }
        store.saveStateAsync(id, null, snapshot());
        stateDirty = false;
    }

    @Override
    protected void periodicSync() {
        if (stateDirty && outTxState != OutTxState.COMPLETED) {
            persistState();
        }
    }

    @Override
    protected boolean persistOnShutdown() {
        if (!persist || storageReleased || outTxState == OutTxState.COMPLETED) {
            return false;
        }
        stopSending();
        eofTimer.cancel();
        cancelInactivityTimer();
        try {
            store.saveState(id, null, snapshot());
        } catch (IOException e) {
            log.error("TXID{} failed to save the transfer state", cfdpTransactionId, e);
        }
        stopped = true;
        closeSource();
        log.info("TXID{} transfer state saved ({}/{} bytes sent)", cfdpTransactionId, transferred, fileLength);
        return true;
    }

    @Override
    protected void releaseStorage() {
        if (storageReleased) {
            return;
        }
        storageReleased = true;
        closeSource();
        if (store != null) {
            store.deleteAsync(id, null);
        }
    }

    static CfdpHeaderFields headerFields(CfdpHeader h) {
        return CfdpHeaderFields.newBuilder()
                .setEntityIdLength(h.getEntityIdLength())
                .setSequenceNumberLength(h.getSequenceNumberLength())
                .setSourceId(h.getSourceId())
                .setDestinationId(h.getDestinationId())
                .setSequenceNumber(h.getSequenceNumber())
                .setAcknowledged(h.isAcknowledged())
                .setLargeFile(h.isLargeFile())
                .build();
    }

    static CfdpHeader headerFromFields(CfdpHeaderFields h) {
        return new CfdpHeader(true, false, h.getAcknowledged(), false, h.getEntityIdLength(),
                h.getSequenceNumberLength(), h.getSourceId(), h.getDestinationId(), h.getSequenceNumber());
    }

    /**
     * Fills in the part of the snapshot describing the Put.request; this is common to the ongoing and queued transfers.
     */
    static OutgoingTransferSnapshot.Builder requestSnapshot(long initiatorEntityId, PutRequest request, Bucket bucket,
            Integer customPduSize, Integer customPduDelay) {
        var out = OutgoingTransferSnapshot.newBuilder()
                .setSourceId(initiatorEntityId)
                .setDestinationId(request.getDestinationCfdpEntityId())
                .setClosureRequested(request.isClosureRequested())
                .setFileTransfer(request instanceof FilePutRequest)
                .setRequestedFileLength(request.getFileLength());
        if (request.getSourceFileName() != null) {
            out.setSourceFileName(request.getSourceFileName());
            out.setObjectName(request.getSourceFileName());
        }
        if (request.getDestinationFileName() != null) {
            out.setDestinationFileName(request.getDestinationFileName());
        }
        if (request instanceof FilePutRequest fpr) {
            out.setOverwrite(fpr.getOverwrite()).setCreatePath(fpr.getCreatePath());
        }
        if (request.getHeader() != null) {
            out.setRequestHeader(headerFields(request.getHeader()));
        }
        if (request.getMetadata() != null) {
            out.setMetadataPdu(ByteString.copyFrom(request.getMetadata().toByteArray()));
        }
        if (bucket != null) {
            out.setBucket(bucket.getName());
        }
        if (customPduSize != null) {
            out.setCustomPduSize(customPduSize);
        }
        if (customPduDelay != null) {
            out.setCustomPduDelay(customPduDelay);
        }
        return out;
    }

    TransferSnapshot snapshot() {
        var out = requestSnapshot(cfdpTransactionId.getInitiatorEntity(), request, bucket, customPduSize,
                customPduDelay)
                .setOutTxState(OutgoingTransferSnapshot.OutTxState.valueOf(outTxState.name()))
                .setOffset(offset)
                .setTransferred(transferred)
                .setResendMetadata(resendMetadata)
                .setEofSent(eofSent)
                .setEofAckReceived(eofAckReceived)
                .setSuspended(suspended);
        for (SegmentRequest seg : toResend.toList()) {
            out.addToResend(FileSegment.newBuilder().setStart(seg.getSegmentStart()).setEnd(seg.getSegmentEnd()));
        }
        if (source != null) {
            out.setFileSize(fileLength).setFileChecksum((int) fileChecksum);
        }
        if (metadata != null) {
            out.setMetadataPdu(ByteString.copyFrom(metadata.toByteArray()));
        }
        if (eofPacket != null) {
            out.setEofPdu(ByteString.copyFrom(eofPacket.toByteArray()));
        }
        if (reasonForCancellation != null) {
            out.setReasonForCancellation(reasonForCancellation.getCode());
        }
        return snapshotBuilder(SnapshotDirection.OUTGOING, headerFields(directiveHeader)).setOutgoing(out).build();
    }

    /**
     * Rebuilds the Put.request from a snapshot (of an ongoing or queued transfer)
     */
    static PutRequest restoreRequest(TransferSnapshot snapshot, Bucket bucket) {
        var out = snapshot.getOutgoing();
        MetadataPacket md = (MetadataPacket) decodePdu(out.hasMetadataPdu(), out.getMetadataPdu(), "metadata");
        CfdpHeader hdr = out.hasRequestHeader() ? headerFromFields(out.getRequestHeader()) : null;
        var tm = snapshot.getAcknowledged() ? CfdpPacket.TransmissionMode.ACKNOWLEDGED
                : CfdpPacket.TransmissionMode.UNACKNOWLEDGED;
        if (out.getFileTransfer()) {
            if (bucket == null) {
                throw new IllegalArgumentException("no bucket for the file transfer");
            }
            var fpr = new FilePutRequest(out.getSourceId(), out.getDestinationId(), out.getSourceFileName(),
                    out.getDestinationFileName(), out.getOverwrite(), snapshot.getAcknowledged(),
                    out.getClosureRequested(), out.getCreatePath(), bucket, out.getRequestedFileLength());
            if (hdr != null) {
                fpr.setHeaderAndMetadata(hdr, null);
            }
            return fpr;
        } else {
            if (md == null) {
                throw new IllegalArgumentException("no metadata for the metadata only transfer");
            }
            return PutRequest.restore(out.getDestinationId(), tm, out.getClosureRequested(), hdr, md);
        }
    }

    /**
     * Restores an ongoing transfer from a snapshot. If the file has been prepared, the data file is opened and its
     * size and checksum are verified.
     * <p>
     * The transfer is not active (no timer is running, no PDU sent) until {@link #resumeAfterRestore()} is called.
     *
     * @throws IllegalArgumentException
     *             if the snapshot is invalid or the data file is missing, has been modified or is corrupted.
     */
    static CfdpOutgoingTransfer restore(String yamcsInstance, TransferSnapshot snapshot,
            ScheduledThreadPoolExecutor executor, YConfiguration config, Stream cfdpOut, Bucket bucket,
            EventProducer eventProducer, TransferMonitor monitor,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions, CfdpTransferStore store)
            throws IOException {
        if (snapshot.getDirection() != SnapshotDirection.OUTGOING || !snapshot.hasOutgoing()
                || !snapshot.hasHeader()) {
            throw new IllegalArgumentException("not an outgoing transfer snapshot");
        }
        var out = snapshot.getOutgoing();
        PutRequest request = restoreRequest(snapshot, bucket);
        MetadataPacket metadata = (MetadataPacket) decodePdu(out.hasMetadataPdu(), out.getMetadataPdu(), "metadata");
        EofPacket eof = (EofPacket) decodePdu(out.hasEofPdu(), out.getEofPdu(), "EOF");
        // the PDUs continue to use the same header as before the restart
        request.setHeaderAndMetadata(headerFromFields(snapshot.getHeader()), metadata);

        OutTxState outTxState = OutTxState.valueOf(out.getOutTxState().name());
        if (outTxState == OutTxState.COMPLETED) {
            throw new IllegalArgumentException("transfer already completed");
        }
        OutgoingFileSource source = null;
        if (out.getFileTransfer()) {
            if (out.hasFileSize()) {
                source = openAndVerify(store.dataPath(snapshot.getId()), out.getFileSize(),
                        Integer.toUnsignedLong(out.getFileChecksum()));
            } else if (outTxState != OutTxState.START) {
                throw new IllegalArgumentException("the file to be sent has not been saved");
            }
        }

        CfdpOutgoingTransfer trsf;
        try {
            trsf = new CfdpOutgoingTransfer(yamcsInstance, out.getSourceId(), snapshot.getId(),
                    snapshot.getCreationTime(), executor, request, cfdpOut, config, bucket,
                    out.hasCustomPduSize() ? out.getCustomPduSize() : null,
                    out.hasCustomPduDelay() ? out.getCustomPduDelay() : null,
                    eventProducer, monitor, faultHandlerActions, store, true);
        } catch (RuntimeException e) {
            if (source != null) {
                source.close();
            }
            throw e;
        }
        trsf.restoreCommon(snapshot);
        if (source != null) {
            trsf.source = source;
            trsf.fileLength = out.getFileSize();
            trsf.fileChecksum = Integer.toUnsignedLong(out.getFileChecksum());
        }
        trsf.outTxState = outTxState;
        trsf.eofPacket = eof;
        trsf.offset = Math.min(out.getOffset(), trsf.fileLength);
        trsf.transferred = Math.min(out.getTransferred(), trsf.fileLength);
        for (FileSegment seg : out.getToResendList()) {
            trsf.toResend.add(seg.getStart(), Math.min(seg.getEnd(), trsf.fileLength));
        }
        trsf.resendMetadata = out.getResendMetadata();
        trsf.eofSent = out.getEofSent() && eof != null;
        trsf.eofAckReceived = out.getEofAckReceived();
        trsf.suspended = out.getSuspended();
        if (out.hasReasonForCancellation()) {
            trsf.reasonForCancellation = ConditionCode
                    .readConditionCode((byte) (out.getReasonForCancellation() << 4));
        }
        if (outTxState == OutTxState.CANCELING && eof == null) {
            trsf.closeSource();
            throw new IllegalArgumentException("CANCELING state without EOF PDU");
        }
        return trsf;
    }

    /**
     * Opens the data file and verifies its size and checksum.
     */
    static OutgoingFileSource openAndVerify(Path path, long size, long checksum) throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("the data file " + path + " is missing");
        }
        var src = OutgoingFileSource.openFile(path);
        try {
            if (src.size() != size) {
                throw new IllegalArgumentException("the data file " + path + " has been modified: size is "
                        + src.size() + " instead of " + size);
            }
            long cs = src.checksum(size);
            if (cs != checksum) {
                throw new IllegalArgumentException("the data file " + path + " is corrupted: checksum is "
                        + Long.toHexString(cs) + " instead of " + Long.toHexString(checksum));
            }
        } catch (IOException | RuntimeException e) {
            src.close();
            throw e;
        }
        return src;
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
     * Restarts the timers and the sending of PDUs for a restored transfer. Has to be called on the executor thread.
     */
    void resumeAfterRestore() {
        log.info("TXID{} restored in state {}/{} {}; {}/{} bytes sent, to resend: {}", cfdpTransactionId, outTxState,
                state, suspended ? "(suspended)" : "", transferred, fileLength, toResend);
        persistState();
        if (suspended) {
            return;
        }
        switch (outTxState) {
        case START:
            startOrContinue();
            break;
        case SENDING_DATA:
            startSending();
            if (eofSent && !eofAckReceived) {
                sendEof();
            } else if (eofAckReceived) {
                rescheduleInactivityTimer();
            }
            break;
        case CANCELING:
            sendEof();
            break;
        case COMPLETED:
            break;
        }
    }

    boolean isSuspended() {
        return suspended;
    }

    MissingSegments getToResend() {
        return toResend;
    }
}
