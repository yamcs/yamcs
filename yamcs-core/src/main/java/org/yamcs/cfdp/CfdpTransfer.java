package org.yamcs.cfdp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.ActionCode;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FaultHandlerOverride;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FileStoreRequest;
import org.yamcs.cfdp.pdu.LV;
import org.yamcs.cfdp.pdu.MessageToUser;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.TLV;
import org.yamcs.protobuf.Cfdp;
import org.yamcs.protobuf.Cfdp.TransferDirection;
import org.yamcs.protobuf.Cfdp.TransferState;
import org.yamcs.yarch.Stream;

public class CfdpTransfer extends CfdpTransaction {

    private static final Logger log = LoggerFactory.getLogger(CfdpTransfer.class);

    private enum CfdpTransferState {
        START,
        METADATA_SENT,
        SENDING_DATA,
        SENDING_FINISHED,
        EOF_SENT,
        EOF_ACK_RECEIVED,
        FINISHED_RECEIVED,
        FINISHED_ACK_SENT,
        CANCELING,
        CANCELED
    }

    private final boolean withCrc = false;
    private final boolean acknowledged = false;
    private final boolean withSegmentation = false;
    private final int entitySize = 4;
    private final int seqNrSize = 4;
    private final int maxDataSize = 20;

    private long startTime;

    private EofPacket eofPacket;
    private long EOFAckTimer;
    private final long EOFAckTimeoutMs = 3000;
    private final int maxEOFResendAttempts = 5;
    private int EOFSendAttempts = 0;

    private CfdpTransferState currentState;
    private TransferState state;
    private long transferred;

    private int offset = 0;
    private int end = 0;

    private final int pauseBetweenFileDataPackets = 1000;

    private TransferDirection transferDirection;

    private boolean sleeping = false;

    private PutRequest request;

    public CfdpTransfer(PutRequest request, Stream cfdpOut) {
        super(request.getSourceId(), cfdpOut);
        this.request = request;
        this.currentState = CfdpTransferState.START;
        this.state = Cfdp.TransferState.RUNNING;
        this.transferDirection = TransferDirection.UPLOAD;
    }

    public TransferState getTransferState() {
        return this.state;
    }

    public PutRequest getRequest() {
        return request;
    }

    public long getTransferredSize() {
        return this.transferred;
    }

    public boolean isOngoing() {
        return state == TransferState.RUNNING || state == TransferState.PAUSED;
    }

    @Override
    public void run() {
        while (isOngoing()) {
            step();
        }
    }

    @Override
    public void step() {
        switch (currentState) {
        case START:
            this.startTime = System.currentTimeMillis();
            sendPacket(getMetadataPacket());
            this.currentState = CfdpTransferState.METADATA_SENT;
            break;
        case METADATA_SENT:
            offset = 0; // first file data packet starts at the start of the data
            end = Math.min(maxDataSize, request.getPacketLength());
            sendPacket(getNextFileDataPacket());
            transferred = end;
            offset = end;
            this.currentState = CfdpTransferState.SENDING_DATA;
            break;
        case SENDING_DATA:
            if (offset == request.getPacketLength()) {
                this.currentState = CfdpTransferState.SENDING_FINISHED;
            } else {
                try {
                    while (sleeping) {
                        Thread.sleep(100);
                    }
                    Thread.sleep(pauseBetweenFileDataPackets);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                end = Math.min(offset + maxDataSize, request.getPacketLength());
                sendPacket(getNextFileDataPacket());
                transferred += (end - offset);
                offset = end;
            }
            break;
        case SENDING_FINISHED:
            eofPacket = getEofPacket(ConditionCode.NoError);
            sendPacket(eofPacket);
            this.currentState = CfdpTransferState.EOF_SENT;
            EOFAckTimer = System.currentTimeMillis();
            EOFSendAttempts = 1;
            break;
        case EOF_SENT:
            // wait for the EOF_ACK
            if (System.currentTimeMillis() > EOFAckTimer + EOFAckTimeoutMs) {
                if (EOFSendAttempts < maxEOFResendAttempts) {
                    log.info("Resending EOF {} of max {}", EOFSendAttempts + 1, maxEOFResendAttempts);
                    sendPacket(eofPacket);
                    EOFSendAttempts++;
                    EOFAckTimer = System.currentTimeMillis();
                } else {
                    log.info("Resend attempts ({}) of EOF reached", maxEOFResendAttempts);
                    // resend attempts exceeded the limit
                    // TODO, we should issue a "Positive ACK Limit Reached fault" Condition Code (or even call an
                    // appropriate sender FaultHandler. See 4.1.7.1.d
                    this.state = Cfdp.TransferState.FAILED;
                }
            } // else, we wait some more
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
            break;
        case CANCELING:
            sendPacket(getEofPacket(ConditionCode.CancelRequestReceived));
            this.currentState = CfdpTransferState.CANCELED;
            break;

        case CANCELED:
            // TODO, we should we not issue Condition Code (or even call an appropriate sender FaultHandler)?
            this.state = Cfdp.TransferState.FAILED;
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
                acknowledged, // not acknowledged // TODO, is this okay?
                withCrc, // no CRC
                entitySize, // TODO, hardcoded entity length
                seqNrSize, // TODO, hardcoded sequence number length
                getTransactionId().getInitiatorEntity(), // my Entity Id
                request.getDestinationId(), // the id of the target
                this.myId.getSequenceNumber());

        // TODO, only supports the creation of new files at the moment
        List<FileStoreRequest> fsrs = new ArrayList<FileStoreRequest>();
        fsrs.add(new FileStoreRequest(ActionCode.CreateFile, new LV(request.getTargetPath())));

        return new MetadataPacket(
                withSegmentation, // TODO no segmentation
                request.getPacketLength(),
                "", // no source file name, the data will come from a bucket
                request.getTargetPath(),
                fsrs,
                new ArrayList<MessageToUser>(), // no user messages
                new ArrayList<FaultHandlerOverride>(), // no fault handler overides
                new TLV((byte) 0x05, new byte[0]), // empty flow label
                header);
    }

    private FileDataPacket getNextFileDataPacket() {
        CfdpHeader header = new CfdpHeader(
                false, // it's file data
                false, // it's sent towards the receiver
                acknowledged, // not acknowledged // TODO, is this okay?
                withCrc, // no CRC
                entitySize, // TODO, hardcoded entity length
                seqNrSize, // TODO, hardcoded sequence number length
                getTransactionId().getInitiatorEntity(), // my Entity Id
                request.getDestinationId(), // the id of the target
                this.myId.getSequenceNumber());

        FileDataPacket filedata = new FileDataPacket(
                Arrays.copyOfRange(request.getPacketData(), offset, end),
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
                entitySize,
                seqNrSize,
                getTransactionId().getInitiatorEntity(),
                request.getDestinationId(),
                this.myId.getSequenceNumber());

        return new EofPacket(
                code, // TODO, we assume no errors
                0, // TODO checksum
                request.getPacketLength(), // TODO, currently assumes that all data is sent exactly once
                null, // TODO, only if ConditionCode.NoError is sent
                header);
    }

    private AckPacket getAckPacket() {
        CfdpHeader header = new CfdpHeader(
                true, // file directive
                false, // towards receiver
                acknowledged,
                withCrc,
                entitySize,
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

    public TransferDirection getDirection() {
        return this.transferDirection;
    }

    public long getTotalSize() {
        return this.request.getPacketLength();
    }

    public CfdpTransfer cancel() {
        // IF cancelled, return myself, otherwise return null id, otherwise return null
        return this;
    }

    public CfdpTransfer pause() {
        sleeping = true;
        return this;
    }

    public CfdpTransfer resumeTransfer() {
        sleeping = false;
        return this;
    }

    public CfdpTransfer cancelTransfer() {
        sleeping = false; // wake up if sleeping
        currentState = CfdpTransferState.CANCELING;
        return this;
    }

    public long getTransferredBytes() {
        return transferred;
    }

    @Override
    public void processPacket(CfdpPacket packet) {
        if (packet.getHeader().isFileDirective()) {
            switch (((FileDirective) packet).getFileDirectiveCode()) {
            case ACK:
                if (currentState == CfdpTransferState.EOF_SENT && ((AckPacket) packet)
                        .getFileDirectiveSubtypeCode() == FileDirectiveSubtypeCode.FinishedByWaypointOrOther) {
                    currentState = CfdpTransferState.EOF_ACK_RECEIVED;
                }
                break;
            case Finished:
                if (currentState == CfdpTransferState.EOF_ACK_RECEIVED) {
                    currentState = CfdpTransferState.FINISHED_RECEIVED;
                }
                break;
            default:
                break;
            }
        } else {
            // TODO incoming data
        }
    }

}
