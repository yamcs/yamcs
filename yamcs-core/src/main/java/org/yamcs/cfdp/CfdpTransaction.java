package org.yamcs.cfdp;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.api.EventProducer;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.protobuf.Cfdp.TransferDirection;
import org.yamcs.protobuf.Cfdp.TransferState;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public abstract class CfdpTransaction implements Runnable {
    protected CfdpTransactionId myId;
    private Stream cfdpOut;
    protected TransferState state;
    final protected ScheduledThreadPoolExecutor executor;
    final protected EventProducer eventProducer;
    protected boolean acknowledged = false;

    public CfdpTransaction(ScheduledThreadPoolExecutor executor, long initiatorEntity, Stream cfdpOut,
            EventProducer eventProducer) {
        this(executor, new CfdpTransactionId(initiatorEntity), cfdpOut, eventProducer);
    }

    public CfdpTransaction(ScheduledThreadPoolExecutor executor, CfdpTransactionId id, Stream cfdpOut,
            EventProducer eventProducer) {
        this.myId = id;
        this.cfdpOut = cfdpOut;
        this.state = TransferState.RUNNING;
        this.executor = executor;
        this.eventProducer = eventProducer;
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

    public CfdpTransactionId getId() {
        return myId;
    }

    public boolean isReliable() {
        return acknowledged;
    }
}
