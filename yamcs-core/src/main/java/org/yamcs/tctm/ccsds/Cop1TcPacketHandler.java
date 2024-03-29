package org.yamcs.tctm.ccsds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.yamcs.CommandOption;
import org.yamcs.CommandOption.CommandOptionType;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Clcw;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Cop1Config;
import org.yamcs.protobuf.Cop1State;
import org.yamcs.protobuf.Cop1Status;
import org.yamcs.protobuf.TimeoutType;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.ccsds.Cop1Monitor.AlertType;
import org.yamcs.tctm.ccsds.TcManagedParameters.TcVcManagedParameters;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.EnumeratedDataType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;

/**
 * Assembles TC packets into TC frames as per CCSDS 232.0-B-3 and sends them out via FOP1
 * 
 * <p>
 * Implements the FOP (transmitter) part of the Communications Operations Procedure-1 CCSDS 232.1-B-2 September 2010
 * <p>
 * The FOP1 implementation is a little different than the standard: the "Initiate AD service with CLCW check" will wait
 * for the first CLCW and immediately set the vS to the nR in the CLCW. The standard specifies that the vS has somehow
 * to be set manually to an CLCW observed value before calling the "Initiate AD with CLCW check" directive.
 * 
 * @author nm
 *
 */
public class Cop1TcPacketHandler extends AbstractTcDataLink implements VcUplinkHandler {
    static final String[] STATE_NAMES = new String[] { "Invalid", "Active", "Retransmit without wait",
            "Retransmit with wait", "Initialising without BC Frame", "Initialising with BC Frame", "Initial" };

    public static final CommandOption OPTION_BYPASS = new CommandOption("cop1Bypass", "COP-1 Bypass",
            CommandOptionType.BOOLEAN).withHelp("Use BD mode even if AD was initiated.");

    static {
        YamcsServer.getServer().addCommandOption(OPTION_BYPASS);
    }

    static final int OUT_QUEUE_SIZE = 20;

    // the frames to be sent out are placed here
    BlockingQueue<QueuedFrame> outQueue;

    /**
     * If this is true, we transform the BD packets to frames and put them directly on the outQueue
     * <p>
     * if it's false, we put the BD packets in the inQueue to be taken out in between AD packets
     * <p>
     * Note that if the fop1 state is 4,5 or 6 (i.e. initialising) then the BD packets are also put directly in the
     * outQueue
     */
    boolean bdAbsolutePriority;

    /**
     * If this is false, all frames will be sent directly without passing through the FOP1 state machine
     */
    boolean cop1Active = true;
    /**
     * Used if cop1Active = false, if set all the TC packets will be sent in frames with the bypass flag set
     */
    boolean bypassAll = true;

    TcVcManagedParameters vmp;
    TcFrameFactory frameFactory;

    // used to signal to the master channel when data is available on this VC
    private Semaphore dataAvailableSemaphore;
    final ScheduledThreadPoolExecutor executor;

    protected ArrayDeque<PreparedCommand> waitQueue = new ArrayDeque<>();

    static final int INVALID_CLCW = -1;
    /**
     * 1 - Active 2 - Retransmit without wait 3 - Retransmit with wait 4 - Initialising without BC Frame 5 -
     * Initialising with BC Frame 6 - Initial
     */
    int state = 6;

    // _state is seen by external threads
    volatile int externalState = state;

    // V(S) - Transmitter Frame Sequence Number;
    int vS;

    // Sent_Queue;
    QueuedFrame[] sentQueue = new QueuedFrame[256];

    boolean adOutReady = true;
    boolean bcOutReady = true;

    // NN(R) - Expected Acknowledgement Frame Sequence Number - the value of nR from the previous CLCW
    int nnR;

    // Timer_Initial_Value (also known as ‘T1_Initial’) in milliseconds
    private long t1Initial;

    // Transmission_Limit;
    int txLimit = 3;

    // Transmission_Count;
    int txCount;

    // FOP_Sliding_Window_Width (also known as ‘K’);
    int slidingWindowWidth = 10;

    // Timeout_Type (TT);
    // 1-> SUSPEND, 0 -> go to uninitialized state
    int timeoutType = 1;

    // Suspend_State (SS).
    // 0 = not suspended
    // 1-4 = state in which has been suspended (see state above)
    int suspendState;
    AtomicInteger _clcw = new AtomicInteger(INVALID_CLCW);

    // these are received in the CLCW
    byte clcwLockout;
    byte clcwWait;
    byte clcwRetransmit;

    // timestamp of the reception of the last clcw
    long clcwTimestamp = TimeEncoding.INVALID_INSTANT;

    // used at startup for the initial CLCW wait time
    long initialClcwWait;

    /** N(R) - The Next Expected Frame Sequence Number as received in the last CLCW */
    int nR;

    ScheduledFuture<?> timer;

    private QueuedFrame pendingBCFrame;

    CopyOnWriteArrayList<Cop1Monitor> monitors = new CopyOnWriteArrayList<>();

    final int vcId;
    String clcwStreamName;
    ClcwStreamHelper clcwHelper;

    protected Parameter spCop1Status;
    private volatile ParameterValue cop1Status;

    public Cop1TcPacketHandler(String yamcsInstance, String linkName,
            TcVcManagedParameters vmp, ScheduledThreadPoolExecutor executor) {
        super.init(yamcsInstance, linkName, vmp.config);

        this.frameFactory = vmp.getFrameFactory();
        this.executor = executor;
        this.vmp = vmp;
        this.vcId = vmp.vcId;
        outQueue = new ArrayBlockingQueue<>(OUT_QUEUE_SIZE);
        clcwStreamName = vmp.config.getString("clcwStream");
        this.initialClcwWait = 1000 * vmp.config.getInt("initialClcwWait", -1);
        this.t1Initial = 1000 * vmp.config.getInt("cop1T1", 3);
        this.txLimit = vmp.config.getInt("cop1TxLimit", 3);
        this.slidingWindowWidth = vmp.config.getInt("slidingWindowWidth", 10);
    }

    public void addMonitor(Cop1Monitor monitor) {
        monitors.add(monitor);
    }

    public void removeMonitor(Cop1Monitor monitor) {
        monitors.remove(monitor);
    }

    @Override
    public boolean sendCommand(PreparedCommand pc) {
        boolean tcBypassFlag = isBypass(pc);
        log.debug("state: {}; Received new TC: {}, cop1Bypass: {}, bypassAll: {}", strState(), pc.getLoggingId(),
                tcBypassFlag,
                bypassAll);
        int framingLength = frameFactory.getFramingLength(vmp.vcId);
        int pcLength = cmdPostProcessor.getBinaryLength(pc);
        if (framingLength + pcLength > vmp.maxFrameLength) {
            log.warn("Command {} does not fit into frame ({} + {} > {})", pc.getId(), framingLength, pcLength,
                    vmp.maxFrameLength);
            failedCommand(pc.getCommandId(),
                    "Command too large to fit in a frame; cmd size: " + pcLength + "; max frame length: "
                            + vmp.maxFrameLength + "; frame overhead: " + framingLength);
            return true;
        }

        if (!cop1Active) {
            sendSingleTc(pc, bypassAll || tcBypassFlag);
        } else if ((vmp.bdAbsolutePriority || externalState >= 3) && tcBypassFlag) {
            sendSingleTc(pc, true);
        } else {
            executor.submit(() -> {
                queueTC(pc);
            });
        }
        return true;
    }

    private String strState() {
        return STATE_NAMES[externalState];
    }

    @Override
    public TcTransferFrame getFrame() {
        QueuedFrame qf = outQueue.poll();
        if (qf == null) {
            return null;
        }
        if (qf.cf != null) {
            qf.cf.complete(null);
        }
        frameFactory.encodeFrame(qf.tf);
        // BC frames contain no command but we still count it as one item out
        var count = qf.tf.commands == null ? 1 : qf.tf.commands.size();
        dataOut(count, qf.tf.getData().length);
        return qf.tf;
    }

    private void sendSingleTc(PreparedCommand pc, boolean bypass) {
        TcTransferFrame tf = makeFrame(pc, bypass);
        if (tf != null) {
            boolean added = outQueue.offer(new QueuedFrame(tf));
            if (!added) {
                failedCommand(pc.getCommandId(), "OutQueue on link " + linkName + " full");
            } else {
                signalDataAvailable();
            }
        }
    }

    private TcTransferFrame makeFrame(PreparedCommand pc, boolean bypassFlag) {
        byte[] binary = postprocess(pc);
        if (binary == null) {
            return null;
        }

        TcTransferFrame tf = frameFactory.makeFrame(vmp.vcId, binary.length, pc.getGenerationTime());
        tf.setCommands(Arrays.asList(pc));

        byte[] data = tf.getData();
        int offset = tf.getDataStart();
        System.arraycopy(binary, 0, data, offset, binary.length);

        tf.setBypass(bypassFlag);
        return tf;
    }

    private boolean isBypass(PreparedCommand pc) {
        CommandHistoryAttribute cha = pc.getAttribute(OPTION_BYPASS.getId());
        if (cha == null) {
            return false;
        } else {
            return cha.getValue().getBooleanValue();
        }
    }

    /**
     * Makes a frame from the packets in the {@link #waitQueue} or returns null if the queue is empty
     * <p>
     * All encountered byPass packets are put directly into the out queue
     * 
     * @return
     */
    private TcTransferFrame getNextQueuedDFrame() {
        if (waitQueue.isEmpty()) {
            return null;
        }
        PreparedCommand pc;

        int framingLength = frameFactory.getFramingLength(vmp.vcId);
        int dataLength = 0;
        List<PreparedCommand> l = new ArrayList<>();

        while ((pc = waitQueue.poll()) != null) {
            if (isBypass(pc)) {
                sendSingleTc(pc, true);
                continue;
            }
            int pcLength = cmdPostProcessor.getBinaryLength(pc);
            if (framingLength + dataLength + pcLength <= vmp.maxFrameLength) {
                l.add(pc);
                dataLength += pcLength;
                if (!vmp.multiplePacketsPerFrame) {
                    break;
                }
            } else { // command doesn't fit into frame
                waitQueue.addFirst(pc);
                break;
            }
        }
        if (l.isEmpty()) {
            return null;
        }
        TcTransferFrame tf = frameFactory.makeFrame(vmp.vcId, dataLength, l.get(0).getGenerationTime());
        tf.setCommands(l);

        byte[] data = tf.getData();
        int offset = tf.getDataStart();
        for (PreparedCommand pc1 : l) {
            byte[] binary = postprocess(pc1);
            if (binary == null) {
                continue;
            }

            int length = binary.length;
            if (offset + length > data.length) {
                log.error("TC of length " + length + " does not fit into the frame of length " + data.length
                        + " at offset " + offset);
                if (length != cmdPostProcessor.getBinaryLength(pc1)) {
                    log.error(
                            "Command postprocessor {} getBinaryLength() returned {} but the binary command length returned by process() is {}",
                            cmdPostProcessor.getClass().getName(), cmdPostProcessor.getBinaryLength(pc1), length);
                }
                return null;
            }
            System.arraycopy(binary, 0, data, offset, length);
            offset += length;
        }
        return tf;
    }

    @Override
    public long getFirstFrameTimestamp() {
        QueuedFrame qf = outQueue.peek();
        if (qf == null) {
            return TimeEncoding.INVALID_INSTANT;
        } else {
            return qf.tf.getGenerationTime();
        }

    }

    public CompletableFuture<Void> setVs(int vs) {
        return doInExecutor(cf -> {// E35 Rev. B
            traceEvent("E35 Rev. B");
            if (state != 6) {
                cf.completeExceptionally(
                        new Fop1Exception("Invalid state " + state + " for this operation (should be in state 6)"));
            } else {
                if (suspendState == 0) {
                    this.vS = vs;
                    this.nnR = vs;
                    cf.complete(null);
                } else {
                    cf.completeExceptionally(
                            new Fop1Exception(
                                    "Invalid state " + state + " for this operation (suspendState should be 0)"));
                }
            }
        });
    }

    /**
     * Initiate AD with or without CLCW check
     * 
     * The returned future will be completed when the operation has been initiated.
     * 
     * 
     * @param clcwCheck
     *            - if true, a CLCW will be expected from the remote system and used to initialise the vS. - If false,
     *            the current value of vS will be used.
     */
    public CompletableFuture<Void> initiateAD(boolean clcwCheck, long waitMillisec) {
        return doInExecutor(cf -> {
            if (!preInitCheck(cf)) {
                return;
            }

            log.info("VC {} state: {} Initiating AD {} CLCW check", vcId, state,
                    clcwCheck ? "with " + (waitMillisec / 1000) + " seconds timeout" : "without");
            if (!clcwCheck) {// E23
                traceEvent("E23");
                initialize();
                changeState(1);
            } else { // E24
                traceEvent("E24");
                initialize();
                if (timer != null) {
                    timer.cancel(true);
                }
                timer = executor.schedule(() -> onTimerExpiration(), waitMillisec, TimeUnit.MILLISECONDS);
                changeState(4);
            }
            cf.complete(null);
        });
    }

    public CompletableFuture<Void> initiateAD(boolean clcwCheck) {
        return initiateAD(clcwCheck, t1Initial);
    }

    /**
     * Initiate AD with set V(R). This will cause a BC frame to be sent to the remote system.
     * <p>
     * The returned future is completed as soon as a BC frame has been sent downstream (could be unsuccessful!).
     * 
     * @param vR
     */
    public CompletableFuture<Void> initiateADWithVR(int vR) {
        if (vR < 0 || vR > 255) {
            throw new IllegalArgumentException("vR has to be between 0 and 255 (inclusive)");
        }
        return doInExecutor(cf -> {
            if (!preInitCheck(cf)) {
                return;
            }
            log.info("VC {} state: {} Initiating AD with vR {}", vcId, state, vR);
            if (bcOutReady) {// E27 Rev.B
                traceEvent("E27 Rev.B");
                initialize();
                vS = vR;
                nnR = vR;
                TcTransferFrame ttf = frameFactory.makeFrame(vcId, 3);
                ttf.setBypass(true);
                ttf.setCmdControl(true);
                byte[] data = ttf.getData();
                // CCSDS 232.0-B-3 September 2015, Page 4-9
                int offset = ttf.getDataStart();
                data[offset++] = (byte) 0x82;
                data[offset++] = 0;
                data[offset] = (byte) vR;
                frameFactory.encodeFrame(ttf);

                transmitBCFrame(ttf);
                changeState(5);
                cf.complete(null);
            } else {// E28
                traceEvent("E28");
                cf.completeExceptionally(new Fop1Exception("BC out is not ready"));
            }
        });
    }

    /**
     * Initiate AD with Unlock. This causes a BC Unlock frame to be sent to the remote system.
     */
    public CompletableFuture<Void> initiateADWithUnlock() {
        return doInExecutor(cf -> {
            if (!preInitCheck(cf)) {
                return;
            }
            log.info("VC {} state: {} Initiating AD with Unlock", vcId, state);
            if (bcOutReady) {// E25 Rev.B
                traceEvent("E25 Rev.B");
                initialize();
                TcTransferFrame ttf = frameFactory.makeFrame(vcId, 1);
                ttf.setBypass(true);
                ttf.setCmdControl(true);
                byte[] data = ttf.getData();
                // CCSDS 232.0-B-3 September 2015, Page 4-9
                int offset = ttf.getDataStart();
                data[offset] = 0;
                frameFactory.encodeFrame(ttf);

                transmitBCFrame(ttf);
                changeState(5);
                cf.complete(null);
            } else {
                cf.completeExceptionally(
                        new Fop1Exception("Invalid state for this operation (BC out is not ready)"));
            }
        });
    }

    private boolean preInitCheck(CompletableFuture<Void> cf) {
        if (cop1Active) {
            if (state != 6) {
                cf.completeExceptionally(new Fop1Exception("Invalid state for the init operation (state should be 6)"));
                return false;
            }
        } else {
            cop1Active = true;
            state = 6;
        }
        return true;
    }

    /**
     * Terminate the AD service
     * 
     * @return
     */
    public CompletableFuture<Void> terminateAD() {// E29
        return doInExecutor(cf -> {
            traceEvent("E29");
            if (state != 6) {
                log.info("VC {} state: {} Terminate AD service", vcId, state);
                changeState(6);
                alert(AlertType.TERM);
            }
            cf.complete(null);
        });
    }

    /**
     * Set the timeout type. It can take two values:
     * <ul>
     * <li><i>0:</i> when the timer expires and the transmission limit has been reached, then go to state 6 removing all
     * commands from the queue.</li>
     * <li><i>1:</i> when the timer expires and the transmission limit has been reached, then suspend the operation
     * remembering the state. The operations can be resumed by invoking the resume method.</li>
     * </ul>
     * 
     * @param tt
     * @return
     */
    void setTimeoutType(int tt) {
        if (tt != 0 && tt != 1) {
            throw new IllegalArgumentException(
                    "Timeout type has to be 0 (do not suspend in case of timeout) or 1 (suspend in case of timeout).");
        }
        traceEvent("E39");
        this.timeoutType = tt;
    }

    /**
     * Set the FOP sliding window with - that is the maximum number of commands that can be unacknoledged at one time.
     * 
     * @param K
     * @return
     */
    public CompletableFuture<Void> setWindowWidth(int K) {
        if (K < 1 || K > 255) {
            throw new IllegalArgumentException(
                    "Window with has to be between 1 and 255.");
        }
        return doInExecutor(cf -> {
            traceEvent("E39");
            this.slidingWindowWidth = K;
            cf.complete(null);
        });
    }

    private void transmitBCFrame(TcTransferFrame ttf) {
        pendingBCFrame = new QueuedFrame(ttf);
        txCount = 1;
        sendBCDownstream();
    }

    /**
     * Resume the AD service (if it is suspended)
     * 
     * @return
     */
    public CompletableFuture<Void> resume() {
        return doInExecutor(cf -> {
            if (suspendState == 0) {// E30
                traceEvent("E30");
                cf.completeExceptionally(
                        new Fop1Exception("Invalid state for this operation (suspendState should not be 0)"));
            } else if (suspendState <= 4) {// E31 Rev.B E32 Rev.B E33 Rev.B E34 Rev.B
                traceEvent("E31 Rev.B E32 Rev.B E33 Rev.B E34 Rev.B");
                if (state == 6) {
                    int _ss = suspendState;
                    doResume();
                    changeState(_ss);
                    cf.complete(null);
                }
            }
        });
    }

    public void purgeSentQueue() {
        for (int i = 0; i < 255; i++) {
            sentQueue[i] = null;
        }
        nnR = vS;
    }

    public void purgeWaitQueue() {
        waitQueue.clear();
    }

    private void queueTC(PreparedCommand pc) {
        log.debug("Adding command {} to the waitQueue", pc.getLoggingId());
        waitQueue.add(pc);
        monitors.forEach(m -> m.tcQueued());
        if (state <= 2) {
            lookForFDU();
        }

    }

    /**
     * Set the value of the t1Initial - this is the value used to initialize the timer.
     * 
     * @param t1Initial
     */
    void setT1Initial(long t1Initial) {
        this.t1Initial = t1Initial;
    }

    void setTransmissionLimit(int txLimit) {
        this.txLimit = txLimit;
    }

    private void sendBCDownstream() {
        bcOutReady = false;
        startTimer();

        queueForDownstream(pendingBCFrame).handleAsync((v, t) -> {
            bcOutReady = true;
            if (t == null) {// E43
                traceEvent("E43");
                if (state == 5) {
                    lookForDirective();
                }
            } else if (!(t instanceof CancellationException)) { // E44
                traceEvent("E44");
                alert(AlertType.LLIF);
                changeState(6);
            }
            return null;
        }, executor);
    }

    private void sendADDownstream(QueuedFrame qf) {
        adOutReady = false;
        startTimer();
        queueForDownstream(qf).handleAsync((v, t) -> {
            if (t == null) {//// E41
                traceEvent("E41");
                adOutReady = true;
                if (state <= 2) {
                    lookForFDU();
                }
            } else if (!(t instanceof CancellationException)) { // E41
                traceEvent("E41");
                alert(AlertType.LLIF);
                changeState(6);
            }
            return null;
        }, executor);

    }

    /**
     * Called when a new CLCW is received from the remote system
     * 
     * @param clcw
     */
    public void onCLCW(int clcw) {
        int tmp = _clcw.getAndSet(clcw);
        if (tmp == INVALID_CLCW) {
            executor.execute(() -> {
                _onCLCWUpdate();
            });
        } // else the previous CLCW has not been processed so this one will be processed instead
    }

    private CompletableFuture<Void> doInExecutor(Consumer<CompletableFuture<Void>> task) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            task.accept(cf);
        });
        return cf;
    }

    private void initialize() {
        purgeSentQueue();
        purgeWaitQueue();
        txCount = 1;
        suspendState = 0;
    }

    private void doResume() {
        startTimer();
        suspendState = 0;
    }

    private void _onCLCWUpdate() {
        final int clcwn = _clcw.getAndSet(INVALID_CLCW);
        if (!parseCLCW(clcwn)) {
            return;
        }

        if (state == 6) {
            return;
        }

        if (clcwLockout == 1) {// E14
            traceEvent("E14");
            if (state <= 4) {
                alert(AlertType.LOCKOUT);
                changeState(6);
            }
            return;
        }

        if (nR == vS) {
            if (clcwRetransmit == 0) { //
                if (clcwWait == 0) {
                    if (state == 4) {
                        traceEvent("E1");
                        timer.cancel(true);
                        changeState(1);
                    } else if (state == 5) {
                        traceEvent("E1");
                        timer.cancel(true);
                        pendingBCFrame = null;
                        changeState(1);
                    } else {
                        if (nR == nnR) {// E1
                            traceEvent("E1");
                            if (state == 2 || state == 3) {
                                alert(AlertType.SYNCH);
                                changeState(6);
                            }
                        } else {// nr != nnR // E2
                            traceEvent("E2");
                            if (state <= 3) {
                                timer.cancel(false);
                                removeAcknowlegedFramesFromSentQueue();
                                lookForFDU();
                                changeState(1);
                            }
                        }
                    }
                } else { // clcwWait = 1 // E3
                    traceEvent("E3");
                    if (state <= 5) {
                        alert(AlertType.CLCW);
                        changeState(6);
                    }
                }
            } else { // clcwRetransmit = 1 // E4
                traceEvent("E4");
                if (state <= 4) {
                    alert(AlertType.SYNCH);
                    changeState(6);
                }
            }
        } else if (checkNnrNrVsSeq()) {
            if (clcwRetransmit == 0) {
                if (clcwWait == 0) {
                    if (nR == nnR) {// E5
                        traceEvent("E5");
                        if (state == 2 || state == 3) {
                            alert(AlertType.SYNCH);
                            changeState(6);
                        }
                    } else {// E6 Rev.B
                        traceEvent("E6 Rev.B");
                        if (state <= 3) {
                            removeAcknowlegedFramesFromSentQueue();
                            lookForFDU();
                            changeState(1);
                        }
                    }
                } else { // nR<vS, clcwRetransmit = 0, clcWait = 1 // E7 Rev.B
                    traceEvent("E7 Rev.B");
                    if (state <= 3) {
                        alert(AlertType.CLCW);
                        changeState(6);
                    }
                }
            } else { // nR<vS, clcwRetransmit = 1
                if (txLimit == 1) {
                    traceEvent("E101 and E102");
                    if (state <= 3) {
                        removeAcknowlegedFramesFromSentQueue();
                        alert(AlertType.LIMIT);
                        changeState(6);
                    }
                } else { // clcwRetransmit = 1, txLimit > 1
                    if (nR != nnR) {
                        if (clcwWait == 0) {// E8 Rev.B
                            traceEvent("E8 Rev.B");
                            if (state <= 3) {
                                removeAcknowlegedFramesFromSentQueue();
                                initiateADRetransmission();
                                lookForFDU();
                                changeState(2);
                            }
                        } else {// clcwRetransmit = 1, txLimit > 1, nr!=nnR, clcWait = 1 // E9 Rev.B
                            traceEvent("E9 Rev.B");
                            if (state <= 3) {
                                removeAcknowlegedFramesFromSentQueue();
                                changeState(3);
                            }
                        }
                    } else { // clcwRetransmit = 1, txLimit > 1, nr = nnR
                        if (txCount < txLimit) {
                            if (clcwWait == 0) {// clcwRetransmit = 1, txLimit > 1, nr = nnR, clcwWait = 0 // E10.
                                                // Rev B
                                traceEvent("E10. Rev B");
                                if (state == 1 || state == 3) {
                                    initiateADRetransmission();
                                    lookForFDU();
                                    changeState(2);
                                }
                            } else {// clcwRetransmit = 1, txLimit > 1, nr = nnR, clcwWait = 1 // E11. RevB
                                traceEvent("E11. RevB");
                                if (state < 3) {
                                    changeState(3);
                                }
                            }
                        } else {
                            if (clcwWait == 0) {// clcwRetransmit = 1, txLimit>1, nr = nnR
                                                // txCount = txLimit, clcWait = 0
                                traceEvent("E12 Rev B");
                                if (state == 1 || state == 3) {
                                    changeState(2);
                                }
                            } else { // clcwRetransmit = 1, txLimit>1, nr = nnR, txCount = txLimit, clcWait = 1 //
                                     // E103
                                traceEvent("E103");
                                if (state < 3) {
                                    changeState(3);
                                }
                            }
                        }
                    }
                }
            }
        } else {// nR not in between nnR and vS // E13
            traceEvent("E13");
            if (state <= 4) {
                alert(AlertType.NNR);
                changeState(6);
            }
        }

        monitors.forEach(m -> m.clcwReceived(clcwn));
    }

    private void changeState(int newState) {
        int oldState = state;

        if (oldState != newState) {
            state = newState;
            externalState = state;
            monitors.forEach(m -> m.stateChanged(oldState, newState));
        }
    }

    private void lookForFDU() {
        if (adOutReady) {
            int i = nnR;
            while (i != vS) {
                QueuedFrame qf = sentQueue[i];
                if (qf.toBeRetransmitted) {
                    qf.toBeRetransmitted = false;
                    sendADDownstream(qf);
                    return;
                }
                i = incr(i);
            }
            if (sentQueueSize() < slidingWindowWidth) {
                TcTransferFrame tf = getNextQueuedDFrame();
                if (tf != null) {
                    tf.setVcFrameSeq(vS);
                    QueuedFrame qf = new QueuedFrame(tf);

                    sentQueue[vS] = qf;
                    if (nnR == vS) { /// queue empty
                        txCount = 1;
                    }
                    vS = incr(vS);
                    sendADDownstream(qf);
                    monitors.forEach(m -> m.tcSent());
                }
            }
        }
    }

    private void lookForDirective() {
        if (bcOutReady && pendingBCFrame != null && pendingBCFrame.toBeRetransmitted) {
            pendingBCFrame.toBeRetransmitted = false;
            sendBCDownstream();
        }
    }

    private void onTimerExpiration() {
        log.debug("VC {} state: {}, txCount: {}, txLimit: {} timer expired", vcId, state, txCount, txLimit);

        if (txCount < txLimit) {
            if (timeoutType == 0) {// E16.Rev.B
                traceEvent("E16.Rev.B");
                if (state <= 2) {
                    initiateADRetransmission();
                    lookForFDU();
                } else if (state == 4) {
                    alert(AlertType.T1);
                    changeState(6);
                } else if (state == 5) {
                    initiateBCRetransmission();
                    lookForDirective();
                }
            } else {// timeoutType = 1 //E104
                traceEvent("E104");
                if (state <= 2) {
                    initiateADRetransmission();
                    lookForFDU();
                } else if (state == 4) {
                    suspendState = 4;
                    monitors.forEach(m -> m.suspended(suspendState));
                    changeState(6);
                } else if (state == 5) {
                    initiateBCRetransmission();
                    lookForDirective();
                }
            }
        } else {// txCount = txLimit
            if (timeoutType == 0) {// E17. Rev.B
                traceEvent("E17. Rev.B");
                alert(AlertType.T1);
                changeState(6);
            } else {// E18. Rev.B
                traceEvent("E18. Rev.B");
                if (state <= 4) {
                    log.debug("VC {} FOP-1 suspended", vcId);
                    suspendState = state;
                    monitors.forEach(m -> m.suspended(suspendState));
                    changeState(6);
                } else if (state == 5) {
                    alert(AlertType.T1);
                    changeState(6);
                }
            }
        }
    }

    private void initiateBCRetransmission() {
        txCount++;
        pendingBCFrame.toBeRetransmitted = true;
    }

    private void initiateADRetransmission() {
        txCount++;
        int i = nnR;
        while (i != vS) {
            QueuedFrame qf = sentQueue[i];
            qf.toBeRetransmitted = true;
            log.debug("VC {} state: {}, retransmitting frame {}, txCount: {}, txLimit:{}", vcId, state, i, txCount,
                    txLimit);
            i = incr(i);
        }
    }

    private void startTimer() {
        log.trace("starting timer with {} millisec", t1Initial);
        if (timer != null) {
            timer.cancel(true);
        }
        timer = executor.schedule(() -> onTimerExpiration(), t1Initial, TimeUnit.MILLISECONDS);
    }

    private int sentQueueSize() {
        return nnR <= vS ? vS - nnR : vS + 256 - nnR;
    }

    private int incr(int x) {
        return (x + 1) & 0xFF;
    }

    private void removeAcknowlegedFramesFromSentQueue() {
        while (nnR != nR) {
            QueuedFrame qf = sentQueue[nnR];
            qf.cf.complete(null);
            ackFrame(qf.tf);

            nnR = incr(nnR);
        }
        txCount = 1;
    }

    private void alert(AlertType alert) {
        Fop1Exception e = new Fop1Exception(alert);
        int i = nnR;
        while (i != vS) {
            QueuedFrame qf = sentQueue[i];
            if (qf == null) {
                log.error("VC {} Invalid state of the queue sentQueue[{}] is null", vcId, i);
            } else {
                qf.cf.completeExceptionally(e);
                sentQueue[i] = null;
            }
            i = incr(i);
        }
        monitors.forEach(m -> m.alert(alert));
    }

    // check that nR is in between nnR and vS (modulo 256)
    private boolean checkNnrNrVsSeq() {
        if (nnR <= vS) {
            return nnR <= nR && nR <= vS;
        } else {
            return nnR <= nR || nR <= vS;
        }
    }

    private boolean parseCLCW(int clcwn) {
        int rcvVcId = (clcwn >> 18) & 0x3F;

        if (vcId != rcvVcId) {
            log.debug("Ignoring CLCW for VC {}", rcvVcId);
            return false;
        }
        clcwLockout = (byte) ((clcwn >> 13) & 1);
        clcwWait = (byte) ((clcwn >> 12) & 1);
        clcwRetransmit = (byte) ((clcwn >> 11) & 1);
        nR = clcwn & 0xFF;
        clcwTimestamp = TimeEncoding.getWallclockTime();

        if (state == 4) {
            vS = nR;
            nnR = nR;
        }

        if (log.isTraceEnabled()) {
            log.trace("VC {} state {} received CLCW: lockout: {}, wait: {}, retransmit: {}, nR: {}", vcId, state,
                    clcwLockout, clcwWait, clcwRetransmit, nR);
        }
        return true;
    }

    private CompletableFuture<Void> queueForDownstream(QueuedFrame qf) {
        qf.cf = new CompletableFuture<>();
        outQueue.add(qf);

        signalDataAvailable();
        return qf.cf;
    }

    private void signalDataAvailable() {
        if (dataAvailableSemaphore != null) {
            dataAvailableSemaphore.release();
        }
    }

    private void traceEvent(String ev) {
        if (log.isTraceEnabled()) {
            log.trace("VC {} state: {}, nR:{}, nnR:{}, vS: {}, event: {}", vcId, state, nR, nnR, vS, ev);
        }
    }

    @Override
    protected void doStart() {
        clcwHelper = new ClcwStreamHelper(yamcsInstance, clcwStreamName);
        clcwHelper.onClcw(clcw -> onCLCW(clcw));
        if (initialClcwWait > 0) {
            initiateAD(true, initialClcwWait);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        clcwHelper.quit();
        notifyStopped();
    }

    public Semaphore getDataAvailableSemaphore() {
        return dataAvailableSemaphore;
    }

    public void disableCop1(boolean bypassAll) {
        purgeSentQueue();
        this.cop1Active = false;
        this.bypassAll = bypassAll;
        monitors.forEach(m -> m.disabled());
    }

    /**
     * The semaphore will be used by the data link to signal when there is some data to be transmitted.
     * <p>
     * A permit will be released in these circumstances
     * <ul>
     * <li>a new AD telecommand has been placed into the incoming queue;</li>
     * <li>a BC frame has been released</li>
     * <li>a BD command has been released
     * <li>
     * </ul>
     * 
     * Note that if two commands are placed, two permits will be released but the commands might be put in the same
     * frame. This means that the number of permits released is not exactly the same with the number of frames ready to
     * be sent. However the semaphore is used by the master channel to avoid pooling each VC in turn even though no data
     * is available.
     * 
     * @param dataAvailableSemaphore
     */
    @Override
    public void setDataAvailableSemaphore(Semaphore dataAvailableSemaphore) {
        this.dataAvailableSemaphore = dataAvailableSemaphore;
    }

    static class QueuedFrame {
        final TcTransferFrame tf;
        CompletableFuture<Void> cf;
        boolean toBeRetransmitted;

        public QueuedFrame(TcTransferFrame tf) {
            this.tf = tf;
        }
    }

    @Override
    public VcUplinkManagedParameters getParameters() {
        return vmp;
    }

    public CompletableFuture<Void> setConfig(Cop1Config config) {
        return doInExecutor(cf -> {
            if (config.hasBdAbsolutePriority()) {
                bdAbsolutePriority = config.getBdAbsolutePriority();
            }
            if (config.hasTxLimit()) {
                setTransmissionLimit(config.getTxLimit());
            }
            if (config.hasTimeoutType()) {
                setTimeoutType(config.getTimeoutType().getNumber());
            }
            if (config.hasWindowWidth()) {
                setWindowWidth(config.getWindowWidth());
            }
            if (config.hasT1()) {
                setT1Initial(config.getT1());
            }
            cf.complete(null);
        });
    }

    public CompletableFuture<Cop1Config> getCop1Config() {
        CompletableFuture<Cop1Config> cf = new CompletableFuture<>();
        executor.execute(() -> {
            Cop1Config conf = Cop1Config.newBuilder().setBdAbsolutePriority(bdAbsolutePriority)
                    .setT1(t1Initial).setTxLimit(txLimit).setVcId(vcId).setWindowWidth(slidingWindowWidth)
                    .setTimeoutType(TimeoutType.forNumber(timeoutType)).build();
            cf.complete(conf);
        });

        return cf;
    }

    public CompletableFuture<Cop1Status> getCop1Status() {
        CompletableFuture<Cop1Status> cf = new CompletableFuture<>();
        executor.execute(() -> {
            cf.complete(_getCop1Status());
        });

        return cf;
    }

    private Cop1Status _getCop1Status() {
        Cop1Status.Builder cb = Cop1Status.newBuilder().setCop1Active(cop1Active).setNnR(nnR)
                .setTxCount(txCount).setWaitQueueNumTC(waitQueue.size())
                .setSentQueueNumFrames(sentQueueSize()).setOutQueueNumFrames(outQueue.size())
                .setVS(vS);
        if (cop1Active) {
            if (suspendState > 0) {
                cb.setState(Cop1State.SUSPENDED);
            } else {
                cb.setState(Cop1State.forNumber(state));
            }

        } else {
            cb.setSetBypassAll(bypassAll);
        }
        if (clcwTimestamp != TimeEncoding.INVALID_INSTANT) {
            cb.setClcw(Clcw.newBuilder().setLockout(clcwLockout == 1).setNR(nR)
                    .setReceptionTime(TimeEncoding.toProtobufTimestamp(clcwTimestamp)).setWait(clcwWait == 1)
                    .build());
        }

        return cb.build();
    }

    private void ackFrame(TcTransferFrame tcf) {
        for (PreparedCommand pc : tcf.commands) {
            ackCommand(pc.getCommandId());
        }
    }

    @Override
    public void setupSystemParameters(SystemParametersService sysParamsService) {
        super.setupSystemParameters(sysParamsService);

        EnumeratedDataType stateType =
                sysParamsService.createEnumeratedParameterType(Cop1State.class);
        AggregateParameterType aggrType = new AggregateParameterType.Builder().setName("Cop1Status")
                .addMember(new Member("cop1Active", sysParamsService.getBasicType(Type.BOOLEAN)))
                .addMember(new Member("state", stateType))
                .addMember(new Member("waitQueueNumTC", sysParamsService.getBasicType(Type.UINT32)))
                .addMember(new Member("sentQueueNumFrames", sysParamsService.getBasicType(Type.UINT32)))
                .addMember(new Member("vS", sysParamsService.getBasicType(Type.UINT32)))
                .addMember(new Member("nnR", sysParamsService.getBasicType(Type.UINT32)))
                .build();

        spCop1Status = sysParamsService.createSystemParameter(LINK_NAMESPACE + linkName + "/cop1Status", aggrType,
                "Status of the COP1 protocol");

        addMonitor(new Cop1Monitor() {
            int prevClcw = INVALID_CLCW;

            @Override
            public void suspended(int suspendState) {
                updatePv();
            }

            @Override
            public void stateChanged(int oldState, int newState) {
                updatePv();
            }

            @Override
            public void disabled() {
                updatePv();
            }

            @Override
            public void clcwReceived(int clcw) {
                if (clcw != prevClcw) {
                    updatePv();
                    prevClcw = clcw;
                }
            };

            @Override
            public void tcQueued() {
                updatePv();
            };

            @Override
            public void tcSent() {
                updatePv();
            };

            void updatePv() {
                AggregateValue tmp = new AggregateValue(aggrType.getMemberNames());
                tmp.setMemberValue("cop1Active", ValueUtility.getBooleanValue(cop1Active));
                Cop1State c1state = suspendState > 0 ? Cop1State.SUSPENDED : Cop1State.forNumber(state);
                tmp.setMemberValue("state", ValueUtility.getEnumeratedValue(c1state.getNumber(), c1state.name()));
                tmp.setMemberValue("waitQueueNumTC", ValueUtility.getUint32Value(waitQueue.size()));
                tmp.setMemberValue("sentQueueNumFrames", ValueUtility.getUint32Value(sentQueueSize()));
                tmp.setMemberValue("vS", ValueUtility.getUint32Value(vS));
                tmp.setMemberValue("nnR", ValueUtility.getUint32Value(nnR));

                ParameterValue pv = new ParameterValue(spCop1Status);
                pv.setGenerationTime(getCurrentTime());
                pv.setEngValue(tmp);
                cop1Status = pv;
            }

        });
    }

    @Override
    protected void collectSystemParameters(long time, List<ParameterValue> list) {
        super.collectSystemParameters(time, list);
        if (cop1Status != null) {
            list.add(cop1Status);
            cop1Status = null;
        }
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
