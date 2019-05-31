package org.yamcs.cfdp;

import static org.yamcs.cfdp.CfdpService.ETYPE_EOF_LIMIT_REACHED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducer;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FaultHandlerOverride;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FileStoreRequest;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.MessageToUser;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.cfdp.pdu.TLV;
import org.yamcs.protobuf.Cfdp;
import org.yamcs.protobuf.Cfdp.TransferDirection;
import org.yamcs.protobuf.Cfdp.TransferState;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public class CfdpOutgoingTransfer extends CfdpTransaction {

    private static final Logger log = LoggerFactory.getLogger(CfdpOutgoingTransfer.class);

    private enum CfdpTransferState {
        START,
        METADATA_SENT,
        SENDING_DATA,
        RESENDING,
        SENDING_FINISHED,
        EOF_SENT,
        EOF_ACK_RECEIVED,
        FINISHED_RECEIVED,
        FINISHED_ACK_SENT,
        CANCELING,
        CANCELED
    }

    @SuppressWarnings("unused")
    private final boolean unbounded = false; // only known file length transfers
    private final boolean withCrc = false; // no CRCs are used
  
    private final boolean withSegmentation = false; // segmentation not supported
    private int entityIdLength;
    private int seqNrSize;
    private int maxDataSize;

    // maps offsets to FileDataPackets
    private Map<Long, FileDataPacket> sentFileDataPackets = new HashMap<>();
    private Queue<FileDataPacket> toResend;

    private EofPacket eofPacket;
    private long EOFAckTimer;
    private long eofAckTimeoutMs = 3000; // configurable
    private int maxEofResendAttempts = 5; // configurable
    private int EOFSendAttempts = 0;

    private CfdpTransferState currentState;
    private long transferred;

    private long offset = 0;
    private long end = 0;

    private int sleepBetweenPdusMs = 500;

    private boolean sleeping = false;

    private PutRequest request;
    private ScheduledFuture<?> scheduledFuture;

    public CfdpOutgoingTransfer(ScheduledThreadPoolExecutor executor, PutRequest request, Stream cfdpOut,
            YConfiguration conf, EventProducer eventProducer) {
        super(executor, request.getSourceId(), cfdpOut, eventProducer);
        this.entityIdLength = conf.getInt("entityIdLength", 2);
        this.seqNrSize = conf.getInt("sequenceNrLength", 4);
        int maxPduSize = conf.getInt("maxPduSize", 512);
        this.maxDataSize = maxPduSize - 4 - 2 * this.entityIdLength - this.seqNrSize - 4;
        this.eofAckTimeoutMs = conf.getInt("eofAckTimeoutMs", 3000);
        this.maxEofResendAttempts = conf.getInt("maxEofResendAttempts", 5);
        this.sleepBetweenPdusMs = conf.getInt("sleepBetweenPdusMs", 500);

        this.acknowledged = request.isAcknowledged();

        this.request = request;
        this.currentState = CfdpTransferState.START;
        this.state = Cfdp.TransferState.RUNNING;
    }

    @Override
    public Bucket getBucket() {
        return request.getBucket();
    }

    @Override
    public String getObjectName() {
        return request.getObjectName();
    }

    @Override
    public String getRemotePath() {
        return request.getTargetPath();
    }

    @Override
    public long getTransferredSize() {
        return this.transferred;
    }

    @Override
    public void run() {
        if (state == TransferState.RUNNING && !sleeping) {
            step();
        }
    }

    @Override
    public void step() {
     
        switch (currentState) {
        case START:
            sendPacket(getMetadataPacket());
            this.currentState = CfdpTransferState.METADATA_SENT;
            break;
        case METADATA_SENT:
            offset = 0; // first file data packet starts at the start of the data
            end = Math.min(maxDataSize, request.getPacketLength());
            FileDataPacket nextPacket = getNextFileDataPacket();
            sentFileDataPackets.put(0l, nextPacket);
            sendPacket(nextPacket);
            transferred = end;
            offset = end;
            this.currentState = CfdpTransferState.SENDING_DATA;
            break;
        case SENDING_DATA:
            if (offset == request.getPacketLength()) {
                this.currentState = CfdpTransferState.SENDING_FINISHED;
            } else {
                end = Math.min(offset + maxDataSize, request.getPacketLength());
                nextPacket = getNextFileDataPacket();
                sentFileDataPackets.put(offset, nextPacket);
                sendPacket(nextPacket);
                transferred += (end - offset);
                offset = end;
            }
            break;
        case RESENDING:
            if (!toResend.isEmpty()) {
                sendPacket(toResend.poll());
            }
            // else do nothing, we should wait for a FinishPacket
            break;
        case SENDING_FINISHED:
            eofPacket = getEofPacket(ConditionCode.NoError);
            sendPacket(eofPacket);
            this.currentState = CfdpTransferState.EOF_SENT;
            EOFAckTimer = System.currentTimeMillis();
            EOFSendAttempts = 1;
            break;
        case EOF_SENT:
            if (this.acknowledged) {
                // wait for the EOF_ACK
                if (System.currentTimeMillis() > EOFAckTimer + eofAckTimeoutMs) {
                    if (EOFSendAttempts < maxEofResendAttempts) {
                        log.info("Resending EOF {} of max {}", EOFSendAttempts + 1, maxEofResendAttempts);
                        sendPacket(eofPacket);
                        EOFSendAttempts++;
                        EOFAckTimer = System.currentTimeMillis();
                    } else {
                        eventProducer.sendWarning(ETYPE_EOF_LIMIT_REACHED,
                                "Resend attempts (" + maxEofResendAttempts + ") of EOF reached");
                        // resend attempts exceeded the limit
                        // TODO, we should issue a "Positive ACK Limit Reached fault" Condition Code (or even call an
                        // appropriate sender FaultHandler. See 4.1.7.1.d
                        this.state = Cfdp.TransferState.FAILED;
                    }
                } // else, we wait some more
            } else {
                state = TransferState.COMPLETED;
                scheduledFuture.cancel(true);
            }
            break;
        case EOF_ACK_RECEIVED:
            EOFSendAttempts = 0;
            // DO nothing, we're waiting for a finished packet
            break;
        case FINISHED_RECEIVED:
            sendPacket(getAckPacket());
            this.currentState = CfdpTransferState.FINISHED_ACK_SENT;
            break;
        case FINISHED_ACK_SENT:
            // we're done;
            state = TransferState.COMPLETED;
            scheduledFuture.cancel(true);
            break;
        case CANCELING:
            sendPacket(getEofPacket(ConditionCode.CancelRequestReceived));
            this.currentState = CfdpTransferState.CANCELED;
            break;
        case CANCELED:
            this.state = Cfdp.TransferState.FAILED;
            scheduledFuture.cancel(true);
            break;
        default:
            throw new IllegalStateException("packet in unknown/illegal state");
        }
    }

    private MetadataPacket getMetadataPacket() {
        // create packet header
        CfdpHeader header = new CfdpHeader(
                true, // it's a file directive
                false, // it's sent towards the receiver
                acknowledged,
                withCrc, // no CRC
                entityIdLength,
                seqNrSize,
                getTransactionId().getInitiatorEntity(), // my Entity Id
                request.getDestinationId(), // the id of the target
                this.myId.getSequenceNumber());

        return new MetadataPacket(
                withSegmentation,
                request.getPacketLength(),
                "", // no source file name, the data will come from a bucket
                request.getTargetPath(),
                new ArrayList<FileStoreRequest>(),
                new ArrayList<MessageToUser>(), // no user messages
                new ArrayList<FaultHandlerOverride>(), // no fault handler overrides
                null, // no flow label
                header);
    }

    private FileDataPacket getNextFileDataPacket() {
        CfdpHeader header = new CfdpHeader(
                false, // it's file data
                false, // it's sent towards the receiver
                acknowledged,
                withCrc, // no CRC
                entityIdLength,
                seqNrSize,
                getTransactionId().getInitiatorEntity(), // my Entity Id
                request.getDestinationId(), // the id of the target
                this.myId.getSequenceNumber());

        FileDataPacket filedata = new FileDataPacket(
                Arrays.copyOfRange(request.getPacketData(), (int) offset, (int) end),
                offset,
                header);
        return filedata;
    }

    private EofPacket getEofPacket(ConditionCode code) {
        CfdpHeader header = new CfdpHeader(
                true, // file directive
                false, // towards receiver
                acknowledged,
                withCrc,
                entityIdLength,
                seqNrSize,
                getTransactionId().getInitiatorEntity(),
                request.getDestinationId(),
                this.myId.getSequenceNumber());

        return new EofPacket(
                code,
                request.getChecksum(),
                request.getPacketLength(), // known length, in case unbounded transfers become supported, this needs to
                                           // be updated
                getFaultLocation(code), // TODO, only if ConditionCode.NoError is sent
                header);
    }

    private AckPacket getAckPacket() {
        CfdpHeader header = new CfdpHeader(
                true, // file directive
                false, // towards receiver
                acknowledged,
                withCrc,
                entityIdLength,
                seqNrSize,
                getTransactionId().getInitiatorEntity(),
                request.getDestinationId(),
                this.myId.getSequenceNumber());

        return new AckPacket(
                FileDirectiveCode.Finished,
                FileDirectiveSubtypeCode.FinishedByEndSystem,
                ConditionCode.NoError,
                TransactionStatus.Active,
                header);
    }

    public CfdpTransferState getCfdpState() {
        return this.currentState;
    }

    @Override
    public TransferDirection getDirection() {
        return TransferDirection.UPLOAD;
    }

    @Override
    public long getTotalSize() {
        return this.request.getPacketLength();
    }

    private TLV getFaultLocation(ConditionCode code) {
        if (code == ConditionCode.NoError) {
            return null;
        }
        throw new java.lang.UnsupportedOperationException("CFDP ConditionCode " + code + " not yet supported");
    }

    @Override
    public CfdpOutgoingTransfer pause() {
        sleeping = true;
        return this;
    }

    @Override
    public CfdpOutgoingTransfer resumeTransfer() {
        sleeping = false;
        return this;
    }

    @Override
    public CfdpOutgoingTransfer cancelTransfer() {
        sleeping = false; // wake up if sleeping
        currentState = CfdpTransferState.CANCELING;
        return this;
    }

    public void start() {
        scheduledFuture = executor.scheduleAtFixedRate(this, 0, sleepBetweenPdusMs, TimeUnit.MILLISECONDS);
    }

    public long getTransferredBytes() {
        return transferred;
    }
    @Override
    public void processPacket(CfdpPacket packet) {
        executor.submit(() -> doProcessPacket(packet));
    }
    
    private void doProcessPacket(CfdpPacket packet) {
        if (packet.getHeader().isFileDirective()) {
            switch (((FileDirective) packet).getFileDirectiveCode()) {
            case ACK:
                if (currentState == CfdpTransferState.EOF_SENT && ((AckPacket) packet)
                        .getFileDirectiveSubtypeCode() == FileDirectiveSubtypeCode.FinishedByWaypointOrOther) {
                    currentState = CfdpTransferState.EOF_ACK_RECEIVED;
                } else {
                    log.warn("Received ACK packet while in {} state", currentState);
                }
                break;
            case Finished:
                // depending on the conditioncode, the transfer was a success or a failure
                FinishedPacket p = (FinishedPacket) packet;
                if (p.getConditionCode() != ConditionCode.NoError) {
                    // TODO, Ideally we log the Condition Code (or even call an appropriate sender FaultHandler)?
                    this.state = Cfdp.TransferState.FAILED;
                } else {
                    if (currentState == CfdpTransferState.EOF_ACK_RECEIVED ||
                            currentState == CfdpTransferState.RESENDING) {
                        currentState = CfdpTransferState.FINISHED_RECEIVED;
                    }
                }
                break;
            case NAK:
                toResend = new LinkedList<>();
                for (SegmentRequest segment : ((NakPacket) packet).getSegmentRequests()) {
                    toResend.addAll(sentFileDataPackets.entrySet().stream()
                            .filter(x -> segment.isInRange(x.getKey()))
                            .map(Entry::getValue)
                            .collect(Collectors.toList()));
                }
                if (!toResend.isEmpty()) {
                    currentState = CfdpTransferState.RESENDING;
                }
                break;
            default:
                break;
            }
        } else {
            // TODO, unexpected packet
        }
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    @Override
    public boolean pausable() {
        return true;
    }
    
}
