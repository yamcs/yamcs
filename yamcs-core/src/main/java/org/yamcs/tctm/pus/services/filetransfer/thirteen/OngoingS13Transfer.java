package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.ErrorInCommand;
import org.yamcs.Processor;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducer;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TransferState;
import org.yamcs.security.User;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.FileTransferPacket;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.UplinkS13Packet;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;


public abstract class OngoingS13Transfer implements S13FileTransfer {
    protected final S13TransactionId s13TransactionId;
    protected TransferState state;

    private Stream s13Out;
    protected final ScheduledThreadPoolExecutor executor;
    protected final EventProducer eventProducer;
    protected final Log log;
    protected final long startTime;
    protected final long wallclockStartTime;
    protected final long creationTime;

    // Origin
    protected String origin;

    final TransferMonitor monitor;
    final long destinationId;

    // transaction unique identifier (coming from a database)
    final long id;

    protected ScheduledFuture<?> inactivityFuture;
    final long inactivityTimeout;

    Processor processor;
    XtceDb db;

    // accumulate the errors
    List<String> errors = new ArrayList<>();

    enum FaultHandlingAction {
        SUSPEND, CANCEL, ABANDON;

        public static FaultHandlingAction fromString(String str) {
            for (FaultHandlingAction a : values()) {
                if (a.name().equalsIgnoreCase(str)) {
                    return a;
                }
            }
            return null;
        }

        public static List<String> actions() {
            return Arrays.stream(FaultHandlingAction.values()).map(a -> a.name().toLowerCase())
                    .collect(Collectors.toList());
        }
    }

    final Map<ConditionCode, FaultHandlingAction> faultHandlerActions;

    public OngoingS13Transfer(String yamcsInstance, long id, long creationTime, ScheduledThreadPoolExecutor executor,
            YConfiguration config, S13TransactionId s13TransactionId, long destinationId, Stream s13Out,
            EventProducer eventProducer, TransferMonitor monitor,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions) {
        this.s13TransactionId = s13TransactionId;
        this.s13Out = s13Out;
        this.state = TransferState.RUNNING;
        this.executor = executor;
        this.eventProducer = eventProducer;
        this.startTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();
        this.wallclockStartTime = System.currentTimeMillis();
        this.log = new Log(this.getClass(), yamcsInstance);
        this.id = id;
        this.destinationId = destinationId;
        this.creationTime = creationTime;
        if (monitor == null) {
            throw new NullPointerException("the monitor cannot be null");
        }
        this.monitor = monitor;
        this.inactivityTimeout = config.getLong("inactivityTimeout", 10000);
        this.faultHandlerActions = faultHandlerActions;

        processor = YamcsServer.getServer().getInstance(yamcsInstance).getProcessor("realtime");
        db = YamcsServer.getServer().getInstance(yamcsInstance).getMdb();
    }

    public abstract void processPacket(FileTransferPacket packet);

    // FIXME: Propagate user | Include privilege checking?
    public PreparedCommand createS13Telecommand(String fullyQualifiedCmdName, Map<String, Object> assignments, User user) {
        MetaCommand cmd = db.getMetaCommand(fullyQualifiedCmdName);

        PreparedCommand pc = null;
        try {
            pc = processor.getCommandingManager().buildCommand(cmd, assignments, origin, 0, user);
            pc.disableCommandVerifiers(false);
            pc.disableTransmissionConstraints(false);

        } catch (ErrorInCommand e) {
            throw new BadRequestException(e);
        } catch (YamcsException e) { // could be anything, consider as internal server error
            throw new InternalServerErrorException(e);
        }

        return pc;
    }

    protected void pushError(String err) {
        errors.add(err);
    }

    protected void sendPacket(FileTransferPacket packet) {
        UplinkS13Packet pkt = (UplinkS13Packet) packet;
        Tuple t = pkt.toTuple(this);
        
        if (log.isDebugEnabled()) {
            log.debug("TXID{} sending S13 Packet: {}", s13TransactionId, packet);
        }
        s13Out.emitTuple(t);
    }

    public final boolean isOngoing() {
        return state == TransferState.RUNNING || state == TransferState.PAUSED;
    }

    public final TransferState getTransferState() {
        return state;
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    @Override
    public boolean pausable() {
        return true;
    }

    protected abstract void onInactivityTimerExpiration();

    protected void cancelInactivityTimer() {
        if (inactivityFuture != null) {
            inactivityFuture.cancel(false);
        }
    }

    protected void rescheduleInactivityTimer() {
        cancelInactivityTimer();
        inactivityFuture = executor.schedule(this::onInactivityTimerExpiration, inactivityTimeout,
                TimeUnit.MILLISECONDS);
    }

    public OngoingS13Transfer pauseTransfer() {
        executor.submit(this::suspend);
        return this;
    }

    protected abstract void suspend();

    public OngoingS13Transfer resumeTransfer() {
        executor.submit(this::resume);
        return this;
    }

    protected abstract void resume();

    public OngoingS13Transfer cancelTransfer() {
        executor.submit(() -> {
            pushError("Cancel request received");
            cancel(ConditionCode.CANCEL_REQUEST_RECEIVED);
        });
        return this;
    }

    protected abstract void cancel(ConditionCode code);

    public OngoingS13Transfer abandonTransfer(String reason) {
        executor.submit(() -> failTransfer(reason));
        return this;
    }

    // @Override
    public S13TransactionId getTransactionId() {
        return s13TransactionId;
    }

    @Override
    public boolean isReliable() {
        return false;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    protected void failTransfer(String failureReason) {
        pushError(failureReason);
        changeState(TransferState.FAILED);
    }

    protected void changeState(TransferState newState) {
        this.state = newState;
        monitor.stateChanged(this);
    }

    @Override
    public String getFailuredReason() {
        return errors.stream().collect(Collectors.joining("; "));
    }

    @Override
    public long getId() {
        return id;
    }

    protected FaultHandlingAction getFaultHandlingAction(ConditionCode code) {
        FaultHandlingAction action = faultHandlerActions.get(code);
        if (action == null) {
            return FaultHandlingAction.CANCEL;
        } else {
            return action;
        }
    }

    /**
     * Return the entity id of the Sender
     *
     * @return
     */
    public long getInitiatorId() {
        return s13TransactionId.getInitiatorEntityId();
    }

    /**
     * Return the entity id of the Receiver
     *
     * @return
     */
    public long getDestinationId() {
        return destinationId;
    }

    protected void sendInfoEvent(String type, String msg) {
        eventProducer.sendInfo(type, "TXID[" + s13TransactionId + "] " + msg);
    }

    protected void sendWarnEvent(String type, String msg) {
        eventProducer.sendWarning(type, "TXID[" + s13TransactionId + "] " + msg);
    }

    public long getCreationTime() {
        return creationTime;
    }

}
