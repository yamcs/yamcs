package org.yamcs.cfdp;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.cfdp.pdu.ActionCode;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FaultHandlerOverride;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileStoreRequest;
import org.yamcs.cfdp.pdu.LV;
import org.yamcs.cfdp.pdu.MessageToUser;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.TLV;
import org.yamcs.protobuf.Cfdp;
import org.yamcs.protobuf.Cfdp.TransferDirection;
import org.yamcs.protobuf.Cfdp.TransferState;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public class CfdpTransfer extends CfdpTransaction {

    private enum CfdpTransferState {
        START,
        METADATA_SENT,
        SENDING_DATA,
        SENDING_FINISHED,
        EOF_SENT,
        FINISHED_RECEIVED,
        FINISHED_ACK_SENT
    }

    private final boolean withCrc = false;
    private final boolean acknowledged = false;
    private final boolean withSegmentation = false;
    private final int entitySize = 4;
    private final int seqNrSize = 4;

    private long startTime;

    private CfdpTransferState currentState;
    private TransferState state;
    private long transferred;

    private Bucket bucket;
    private String object;
    private String remotePath;
    private TransferDirection transferDirection;

    private long totalSize;

    private PutRequest request;

    public CfdpTransfer(PutRequest request, Stream cfdpOut) {
        super(request.getSourceId(), cfdpOut);
        this.request = request;
        this.currentState = CfdpTransferState.START;
        this.state = Cfdp.TransferState.RUNNING;
    }

    public TransferState getTransferState() {
        return this.state;
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
        CfdpHeader header;
        switch (currentState) {
        case START:
            this.startTime = System.currentTimeMillis();
            // create packet header
            header = new CfdpHeader(
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

            CfdpPacket metadata = new MetadataPacket(
                    withSegmentation, // TODO no segmentation
                    request.getPacketLength(),
                    "", // no source file name, the data will come from a bucket
                    request.getTargetPath(),
                    fsrs,
                    new ArrayList<MessageToUser>(), // no user messages
                    new ArrayList<FaultHandlerOverride>(), // no fault handler overides
                    new TLV((byte) 0x05, new byte[0]), // empty flow label
                    header);
            sendPacket(metadata);
            this.currentState = CfdpTransferState.METADATA_SENT;
            break;
        case METADATA_SENT:
            // create packet header
            header = new CfdpHeader(
                    false, // it's file data
                    false, // it's sent towards the receiver
                    acknowledged, // not acknowledged // TODO, is this okay?
                    withCrc, // no CRC
                    entitySize, // TODO, hardcoded entity length
                    seqNrSize, // TODO, hardcoded sequence number length
                    getTransactionId().getInitiatorEntity(), // my Entity Id
                    request.getDestinationId(), // the id of the target
                    this.myId.getSequenceNumber());

            CfdpPacket filedata = new FileDataPacket(
                    request.getPacketData(),
                    0, // TODO, hardcoded offset, no support for data splitting yet
                    header);

            sendPacket(filedata);
            this.currentState = CfdpTransferState.SENDING_DATA;
            break;
        case SENDING_DATA:
            boolean TODO_finished = true; // TODO
            if (TODO_finished) {
                this.currentState = CfdpTransferState.SENDING_FINISHED;
            }
            break;
        case SENDING_FINISHED:
            header = new CfdpHeader(
                    true, // file directive
                    false, // towards receiver
                    acknowledged,
                    withCrc,
                    entitySize,
                    seqNrSize,
                    getTransactionId().getInitiatorEntity(),
                    request.getDestinationId(),
                    this.myId.getSequenceNumber());

            CfdpPacket eofPacket = new EofPacket(
                    ConditionCode.NoError, // TODO, we assume no errors
                    0, // TODO checksum
                    request.getPacketLength(), // TODO, currently assumes that all data is sent exactly once
                    null, // TODO, only if ConditionCode.NoError is sent
                    header);

            sendPacket(eofPacket);
            this.currentState = CfdpTransferState.EOF_SENT;
            break;
        case EOF_SENT:
            // Do nothing, we're waiting for a FINISHED_RECEIVED packet
            break;
        case FINISHED_RECEIVED:
            // TODO Send FINISHED_Ack_packet and go to FINISHED_ACK_SENT
            // TODO, for now we don't send acknowledgements, so just move on
            this.currentState = CfdpTransferState.FINISHED_ACK_SENT;
            break;
        case FINISHED_ACK_SENT:
            // we're done;
            state = TransferState.COMPLETED;
            break;
        default:
            throw new IllegalStateException("packet in unknown/illegal state");
        }
    }

    public CfdpTransferState getCfdpState() {
        return this.currentState;
    }

    public Bucket getBucket() {
        return this.bucket;
    }

    public String getObjectName() {
        return this.object;
    }

    public String getRemotePath() {
        return this.remotePath;
    }

    public TransferDirection getDirection() {
        return this.transferDirection;
    }

    public long getTotalSize() {
        return this.totalSize;
    }

    public CfdpTransfer cancel() {
        // IF cancelled, return myself, otherwise return null id, otherwise return null
        return this;
    }

    public CfdpTransfer pause() {
        // IF cancelled, return myself, otherwise return null id, otherwise return null
        return this;
    }

    public CfdpTransfer resumeTransfer() {
        // IF cancelled, return myself, otherwise return null id, otherwise return null
        return this;
    }

    @Override
    public void processPacket(CfdpPacket packet) {
        if (packet.getHeader().isFileDirective()) {
            switch (((FileDirective) packet).getFileDirectiveCode()) {
            case Finished:
                if (currentState == CfdpTransferState.EOF_SENT) {
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
