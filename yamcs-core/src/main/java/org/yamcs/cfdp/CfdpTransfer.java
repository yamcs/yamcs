package org.yamcs.cfdp;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.events.EventProducer;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public abstract class CfdpTransfer {
    protected CfdpTransactionId cfdpTransactionId;
    private Stream cfdpOut;
    protected TransferState state;
    final protected ScheduledThreadPoolExecutor executor;
    final protected EventProducer eventProducer;
    protected boolean acknowledged = false;
    protected final Log log;
    protected final long startTime;
    final TransferMonitor monitor;
    static final AtomicInteger idGenerator = new AtomicInteger();
    final int id;
    protected ScheduledFuture<?> inactivityFuture;
    String failureReason;
    
    final long inactivityTimeout;

    public CfdpTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, YConfiguration config,
            long initiatorEntity,
            Stream cfdpOut, EventProducer eventProducer, TransferMonitor monitor) {
        this(yamcsInstance, executor, config, new CfdpTransactionId(initiatorEntity), cfdpOut, eventProducer, monitor);
    }

    public CfdpTransfer(String yamcsInstance, ScheduledThreadPoolExecutor executor, YConfiguration config,
            CfdpTransactionId cfdpTransactionId,
            Stream cfdpOut, EventProducer eventProducer, TransferMonitor monitor) {
        this.cfdpTransactionId = cfdpTransactionId;
        this.cfdpOut = cfdpOut;
        this.state = TransferState.RUNNING;
        this.executor = executor;
        this.eventProducer = eventProducer;
        this.startTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();
        log = new Log(this.getClass(), yamcsInstance);
        this.id = idGenerator.getAndIncrement();
        if (monitor == null) {
            throw new NullPointerException("the monitor cannot be null");
        }
        this.monitor = monitor;
        this.inactivityTimeout = config.getLong("inactivityTimeout", 5000);
    }

    public abstract void processPacket(CfdpPacket packet);

    protected void sendPacket(CfdpPacket p) {
        if (log.isDebugEnabled()) {
            log.debug("CFDP transaction {}, sending PDU: {}", cfdpTransactionId, p);
            log.trace("{}", StringConverter.arrayToHexString(p.toByteArray(), true));
        }
        rescheduleInactivityTimer();
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

    protected abstract void onInactivityTimerExpiration();

    protected void cancelInactivityTimer() {
        if (inactivityFuture != null) {
            inactivityFuture.cancel(false);
        }
    }

    protected void rescheduleInactivityTimer() {
        cancelInactivityTimer();
        inactivityFuture = executor.schedule(() -> onInactivityTimerExpiration(), inactivityTimeout,
                TimeUnit.MILLISECONDS);
    }

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

    public CfdpTransactionId getTransactionId() {
        return cfdpTransactionId;
    }

    public boolean isReliable() {
        return acknowledged;
    }

    public long getStartTime() {
        return startTime;
    }

    void failTransfer(String failureReason) {
        this.failureReason = failureReason;
        changeState(TransferState.FAILED);
    }

    
    protected void changeState(TransferState newState) {
        this.state = newState;
        if (state != TransferState.RUNNING) {
            cancelInactivityTimer();
        }
        monitor.stateChanged(this);
    }

    public String getFailuredReason() {
        return failureReason;
    }

    public int getId() {
        return id;
    }
}
