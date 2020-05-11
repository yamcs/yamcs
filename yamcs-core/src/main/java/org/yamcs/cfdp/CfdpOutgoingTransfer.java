package org.yamcs.cfdp;

import static org.yamcs.cfdp.CfdpService.ETYPE_EOF_LIMIT_REACHED;
import static org.yamcs.cfdp.CfdpService.ETYPE_TRANSFER_FINISHED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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
import org.yamcs.events.EventProducer;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public class CfdpOutgoingTransfer extends CfdpTransfer {

    private enum SenderTransferState {
        START,
        METADATA_SENT,
        SENDING_DATA,
        RESENDING,
        SENDING_FINISHED,
        EOF_SENT,
        EOF_ACK_RECEIVED,
        FINISHED,
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
    private List<FileDataPacket> sentFileDataPackets = new ArrayList<>();
    private Queue<FileDataPacket> toResend;

    private EofPacket eofPacket;
    private long EOFAckTimer;
    private final long eofAckTimeout;
    private final int maxEofResendAttempts;
    private int EOFSendAttempts = 0;

    private SenderTransferState currentState;
    private long transferred;

    private long offset = 0;
    private long end = 0;

    private final int sleepBetweenPdus;

    private boolean sleeping = false;

    private PutRequest request;
    private ScheduledFuture<?> scheduledFuture;
    long startTime;
    FinishedPacket finishedPacket;

    public CfdpOutgoingTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, PutRequest request,
            Stream cfdpOut, YConfiguration config, EventProducer eventProducer) {
        super(yamcsInstance, executor, request.getSourceId(), cfdpOut, eventProducer);
        this.request = request;
        entityIdLength = config.getInt("entityIdLength");
        seqNrSize = config.getInt("sequenceNrLength");
        int maxPduSize = config.getInt("maxPduSize", 512);
        maxDataSize = maxPduSize - 4 - 2 * entityIdLength - seqNrSize - 4;
        eofAckTimeout = config.getInt("eofAckTimeout", 10000);
        maxEofResendAttempts = config.getInt("maxEofResendAttempts", 5);
        sleepBetweenPdus = config.getInt("sleepBetweenPdus", 500);

        acknowledged = request.isAcknowledged();

        currentState = SenderTransferState.START;
        changeState(TransferState.RUNNING);

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

    public void run() {
        if (state == TransferState.RUNNING && !sleeping) {
            step();
        }
    }

    private void step() {
        switch (currentState) {
        case START:
            this.startTime = System.currentTimeMillis();
            sendPacket(getMetadataPacket());
            this.currentState = SenderTransferState.METADATA_SENT;
            break;
        case METADATA_SENT:
            offset = 0; // first file data packet starts at the start of the data
            end = Math.min(maxDataSize, request.getPacketLength());
            FileDataPacket nextPacket = getNextFileDataPacket();
            sentFileDataPackets.add(nextPacket);
            sendPacket(nextPacket);
            transferred = end;
            offset = end;
            this.currentState = SenderTransferState.SENDING_DATA;
            break;
        case SENDING_DATA:
            if (offset == request.getPacketLength()) {
                this.currentState = SenderTransferState.SENDING_FINISHED;
            } else {
                end = Math.min(offset + maxDataSize, request.getPacketLength());
                nextPacket = getNextFileDataPacket();
                sentFileDataPackets.add(nextPacket);
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
            this.currentState = SenderTransferState.EOF_SENT;
            EOFAckTimer = System.currentTimeMillis();
            EOFSendAttempts = 1;
            break;
        case EOF_SENT:
            if (this.acknowledged) {
                // wait for the EOF_ACK
                if (System.currentTimeMillis() > EOFAckTimer + eofAckTimeout) {
                    if (EOFSendAttempts < maxEofResendAttempts) {
                        log.info("Resending EOF {} of max {}", EOFSendAttempts + 1, maxEofResendAttempts);
                        sendPacket(eofPacket);
                        EOFSendAttempts++;
                        EOFAckTimer = System.currentTimeMillis();
                    } else {
                        eventProducer.sendWarning(ETYPE_EOF_LIMIT_REACHED,
                                "Resend attempts (" + maxEofResendAttempts + ") of EOF reached");
                        // resend attempts exceeded the limit
                        changeState(TransferState.FAILED);
                        scheduledFuture.cancel(true);
                    }
                } // else, we wait some more
            } else {
                changeState(TransferState.COMPLETED);

            }
            break;
        case EOF_ACK_RECEIVED:
            EOFSendAttempts = 0;
            // DO nothing, we're waiting for a finished packet
            break;

        case CANCELING:
            sendPacket(getEofPacket(ConditionCode.CancelRequestReceived));
            this.currentState = SenderTransferState.CANCELED;
            break;
        case CANCELED:
            changeState(TransferState.FAILED);
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
                this.cfdpTransactionId.getSequenceNumber());

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
                this.cfdpTransactionId.getSequenceNumber());

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
                this.cfdpTransactionId.getSequenceNumber());

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
                this.cfdpTransactionId.getSequenceNumber());

        return new AckPacket(
                FileDirectiveCode.Finished,
                FileDirectiveSubtypeCode.FinishedByEndSystem,
                ConditionCode.NoError,
                TransactionStatus.Terminated,
                header);
    }

    public SenderTransferState getCfdpState() {
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
        currentState = SenderTransferState.CANCELING;
        return this;
    }

    public void start() {
        scheduledFuture = executor.scheduleAtFixedRate(() -> run(), 0, sleepBetweenPdus, TimeUnit.MILLISECONDS);
    }

    public long getTransferredBytes() {
        return transferred;
    }

    @Override
    public void processPacket(CfdpPacket packet) {
        executor.submit(() -> doProcessPacket(packet));
    }

    private void doProcessPacket(CfdpPacket packet) {
        if (log.isDebugEnabled()) {
            log.debug("CFDP transaction {}, received PDU: {}", cfdpTransactionId, packet);
            log.trace("{}", StringConverter.arrayToHexString(packet.toByteArray(), true));
        }
        if (state == TransferState.COMPLETED || state == TransferState.FAILED) {
            log.info("Ignoring PDU {} for finished transaction {}", packet, cfdpTransactionId);
            return;
        }
        if (packet.getHeader().isFileDirective()) {
            switch (((FileDirective) packet).getFileDirectiveCode()) {
            case ACK:
                if (currentState == SenderTransferState.EOF_SENT && ((AckPacket) packet)
                        .getFileDirectiveSubtypeCode() == FileDirectiveSubtypeCode.FinishedByWaypointOrOther) {
                    currentState = SenderTransferState.EOF_ACK_RECEIVED;
                } else {
                    log.warn("Received ACK packet while in {} state", currentState);
                }
                break;
            case Finished:
                finishedPacket = (FinishedPacket) packet;

                // depending on the conditioncode, the transfer was a success or a failure
                if (finishedPacket.getConditionCode() != ConditionCode.NoError) {
                    sendPacket(getAckPacket());

                    changeState(TransferState.FAILED);
                    currentState = SenderTransferState.FINISHED;
                    long duration = (System.currentTimeMillis() - startTime) / 1000;
                    eventProducer.sendWarning(ETYPE_TRANSFER_FINISHED,
                            "CFDP upload finished with error in " + duration + " seconds: " + request.getObjectName()
                                    + " -> "
                                    + request.getTargetPath() + " error: " + finishedPacket.getConditionCode());
                } else {
                    if (currentState == SenderTransferState.EOF_ACK_RECEIVED
                            || currentState == SenderTransferState.RESENDING) {

                        sendPacket(getAckPacket());
                        changeState(TransferState.COMPLETED);
                        currentState = SenderTransferState.FINISHED;

                        long duration = (System.currentTimeMillis() - startTime) / 1000;
                        eventProducer.sendInfo(ETYPE_TRANSFER_FINISHED,
                                "CFDP upload finished successfully in " + duration + " seconds: "
                                        + request.getObjectName() + " -> "
                                        + request.getTargetPath());
                    } else {
                        log.warn("Transaction {} received bogus finish packet in state {}: {}", cfdpTransactionId,
                                currentState,
                                finishedPacket);
                    }
                }
                break;
            case NAK:
                toResend = new LinkedList<>();
                for (SegmentRequest segment : ((NakPacket) packet).getSegmentRequests()) {
                    toResend.addAll(sentFileDataPackets.stream()
                            .filter(x -> segment.isInRange(x.getOffset()))
                            .collect(Collectors.toList()));
                }
                if (!toResend.isEmpty()) {
                    currentState = SenderTransferState.RESENDING;
                }
                break;
            default:
                break;
            }
        } else {
            log.warn("Unexpected packet {} ", packet);
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

    @Override
    public String getFailuredReason() {
        if (state == TransferState.FAILED && finishedPacket != null) {
            return finishedPacket.getConditionCode().toString();
        } else {
            return null;
        }
    }
}
