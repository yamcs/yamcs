package org.yamcs.cfdp;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.api.EventProducer;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public abstract class CfdpTransfer implements Runnable {
    protected CfdpTransactionId myId;
    private Stream cfdpOut;
    protected TransferState state;
    final protected ScheduledThreadPoolExecutor executor;
    final protected EventProducer eventProducer;
    protected boolean acknowledged = false;
    protected final Log log; 
    protected final long startTime;
    TransferMonitor monitor;
    
    public CfdpTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, long initiatorEntity, Stream cfdpOut,
            EventProducer eventProducer) {
        this(yamcsInstance, executor, new CfdpTransactionId(initiatorEntity), cfdpOut, eventProducer);
    }

    public CfdpTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, CfdpTransactionId id, Stream cfdpOut,
            EventProducer eventProducer) {
        this.myId = id;
        this.cfdpOut = cfdpOut;
        this.state = TransferState.RUNNING;
        this.executor = executor;
        this.eventProducer = eventProducer;
        this.startTime = TimeEncoding.getWallclockTime();//TODO use mission time?
        log = new Log(this.getClass(), yamcsInstance);
        
    }

    public CfdpTransactionId getTransactionId() {
        return this.myId;
    }

    public abstract void step();

    public abstract void processPacket(CfdpPacket packet);

    protected void sendPacket(CfdpPacket p) {
        if (log.isDebugEnabled()) {
            log.debug("CFDP transaction {}, sending PDU: {}", myId, p);
            log.trace("{}", StringConverter.arrayToHexString(p.toByteArray(), true));
        }
        cfdpOut.emitTuple(p.toTuple(this));
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

    public CfdpTransfer pause() {
        // default behavior, do nothing
        return this;
    }

    public CfdpTransfer resumeTransfer() {
        // default behavior, do nothing
        return this;
    }

    public CfdpTransfer cancelTransfer() {
        // default behavior, do nothing
        return this;
    }

    public CfdpTransactionId getId() {
        return myId;
    }

    public boolean isReliable() {
        return acknowledged;
    }

    public long getStartTime() {
        return startTime;
    }
    
    public void setMonitor(TransferMonitor monitor) {
        this.monitor = monitor;
    }
    
    protected void changeState(TransferState newState) {
        this.state = newState;
        if(monitor!=null) {
            monitor.stateChanged(this);
        }
    }
  
    
    abstract public String getFailuredReason();
}
