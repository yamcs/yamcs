package org.yamcs.cfdp;

import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.protobuf.Cfdp.TransferDirection;
import org.yamcs.protobuf.Cfdp.TransferState;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public abstract class CfdpTransaction extends Thread {

    protected CfdpTransactionId myId;
    private Stream cfdpOut;
    protected TransferState state;

    public CfdpTransaction(long initiatorEntity, Stream cfdpOut) {
        this(new CfdpTransactionId(initiatorEntity), cfdpOut);
    }

    public CfdpTransaction(CfdpTransactionId id, Stream cfdpOut) {
        this.myId = id;
        this.cfdpOut = cfdpOut;
        this.state = TransferState.RUNNING;
    }

    public CfdpTransactionId getTransactionId() {
        return this.myId;
    }

    public abstract void step();

    public abstract void processPacket(CfdpPacket packet);

    protected void sendPacket(CfdpPacket p) {
        cfdpOut.emitTuple(p.toTuple(this.myId));
    }

    public final boolean isOngoing() {
        return state == TransferState.RUNNING || state == TransferState.PAUSED;
    }

    public final TransferState getTransferState() {
        return state;
    }

    public abstract Bucket getBucket();

    public abstract String getObjectName();

    public abstract String getRemotePath();

    public abstract TransferDirection getDirection();

    public abstract long getTotalSize();

    public abstract long getTransferredSize();

    public abstract boolean cancellable();

    public abstract boolean pausable();

    public CfdpTransaction pause() {
        // default behavior, do nothing
        return this;
    }

    public CfdpTransaction resumeTransfer() {
        // default behavior, do nothing
        return this;
    }

    public CfdpTransaction cancelTransfer() {
        // default behavior, do nothing
        return this;
    }
}
