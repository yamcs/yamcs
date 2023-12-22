package org.yamcs.archive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.archive.SpeedSpec.Type;
import org.yamcs.mdb.Mdb;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Performs a replay from Yarch So far supported are: TM packets, PP groups, Events, Parameters and Command History.
 * <p>
 * It relies on handlers for each data type. Each handler creates a stream, the streams are merged and the output is
 * sent to the listener This class can also handle pause/resume: simply stop sending data seek: closes the streams and
 * creates new ones with a different starting time.
 * 
 * @author nm
 *
 */
public class YarchReplay implements StreamSubscriber {
    /**
     * maximum time to wait if SPEED is ORIGINAL meaning that if there is a gap in the data longer than this, we
     * continue)
     */
    public final static long MAX_WAIT_TIME = 10000;

    ReplayServer replayServer;
    volatile String streamName;
    volatile boolean quitting = false;
    private volatile ReplayState state = ReplayState.INITIALIZATION;
    static Logger log = LoggerFactory.getLogger(YarchReplay.class.getName());
    private volatile String errorString = "";
    final String instance;
    static AtomicInteger counter = new AtomicInteger();
    Mdb mdb;

    volatile ReplayOptions currentRequest;

    Map<ProtoDataType, ReplayHandler> handlers;

    private long lastDataSentTime = -1; // time when the last data has been sent
    private long lastDataTime; // time of the last data

    private Semaphore semaphore = new Semaphore(0);
    boolean dropTuple = false; // set to true when jumping to a different time
    volatile boolean ignoreClose;
    volatile boolean sleeping;
    ReplayListener listener;
    volatile long replayTime;

    public YarchReplay(ReplayServer replayServer, ReplayOptions rr, ReplayListener listener, Mdb mdb)
            throws YamcsException {
        this.listener = listener;
        this.replayServer = replayServer;
        this.mdb = mdb;
        this.instance = replayServer.getYamcsInstance();
        setRequest(rr);
    }

    private void setRequest(ReplayOptions req) throws YamcsException {
        if (state != ReplayState.INITIALIZATION && state != ReplayState.STOPPED) {
            throw new YamcsException("changing the request only supported in the INITIALIZATION and STOPPED states");
        }

        if (log.isDebugEnabled()) {
            log.debug("Replay request for time: [{}, {}]",
                    (req.hasRangeStart() ? TimeEncoding.toString(req.getRangeStart()) : "-"),
                    (req.hasRangeStop() ? TimeEncoding.toString(req.getRangeStop()) : "-"));
        }

        if (req.hasRangeStart() && req.hasRangeStop() && req.getRangeStart() > req.getRangeStop()) {
            log.warn("throwing new packetexception: stop time has to be greater than start time");
            throw new YamcsException("stop has to be greater than start");
        }

        currentRequest = req;

        handlers = new HashMap<>();

        if (currentRequest.hasParameterRequest()) {
            throw new YamcsException(
                    "The replay cannot handle directly parameters. Please create a replay processor for that");
        }

        if (currentRequest.hasEventRequest()) {
            handlers.put(ProtoDataType.EVENT, new EventReplayHandler());
        }
        if (currentRequest.hasPacketRequest()) {
            handlers.put(ProtoDataType.TM_PACKET, new XtceTmReplayHandler(mdb));
        }
        if (currentRequest.hasPpRequest()) {
            handlers.put(ProtoDataType.PP, new ParameterReplayHandler(mdb));
        }
        if (currentRequest.hasCommandHistoryRequest()) {
            handlers.put(ProtoDataType.CMD_HISTORY, new CommandHistoryReplayHandler(instance, mdb));
        }

        for (ReplayHandler rh : handlers.values()) {
            rh.setRequest(req);
        }
    }

    public ReplayState getState() {
        return state;
    }

    public synchronized void start() {
        switch (state) {
        case RUNNING:
            log.warn("start called when already running, call ignored");
            return;
        case INITIALIZATION:
        case STOPPED:
            try {
                initReplay();
                state = ReplayState.RUNNING;
            } catch (Exception e) {
                log.error("Got exception when creating the stream: ", e);
                errorString = e.toString();
                state = ReplayState.ERROR;
            }
            break;
        case PAUSED:
            state = ReplayState.RUNNING;
            break;
        case ERROR:
        case CLOSED:
            // do nothing?
        }
    }

    private void initReplay() throws StreamSqlException, ParseException {
        streamName = "replay_stream" + counter.incrementAndGet();

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE STREAM " + streamName + " AS ");

        if (handlers.size() > 1) {
            sb.append("MERGE ");
        }
        List<Object> args = new ArrayList<>();

        boolean first = true;
        for (ReplayHandler rh : handlers.values()) {
            SqlBuilder selectCmd = rh.getSelectCmd();
            if (selectCmd != null) {
                args.addAll(selectCmd.getQueryArguments());
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                if (handlers.size() > 1) {
                    sb.append("(");
                }
                sb.append(selectCmd.toString());
                if (handlers.size() > 1) {
                    sb.append(")");
                }
            }
        }

        if (first) {
            if (currentRequest.getEndAction() == EndAction.QUIT) {
                signalStateChange();
            }
            return;
        }

        if (handlers.size() > 1) {
            sb.append(" USING gentime");
        }

        if (handlers.size() > 1 && currentRequest.isReverse()) {
            sb.append(" ORDER DESC");
        }

        String query = sb.toString();
        log.debug("running query {} with args {} ", query, args);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        ydb.execute(query, args.toArray());
        Stream s = ydb.getStream(streamName);

        s.addSubscriber(this);

        lastDataTime = replayTime = currentRequest.playFrom;

        s.start();
    }

    public void seek(long newReplayTime, boolean autostart) throws YamcsException {
        if (newReplayTime < currentRequest.rangeStart) {
            newReplayTime = currentRequest.rangeStart;
        }
        log.debug("Seek at {} autostart: {}", TimeEncoding.toString(newReplayTime), autostart);
        closeExistingStream();
        lastDataSentTime = -1;
        lastDataTime = newReplayTime;

        currentRequest.setPlayFrom(newReplayTime);
        for (ReplayHandler rh : handlers.values()) {
            rh.setRequest(currentRequest);
        }
        if (autostart) {
            start();
        }
    }

    public void changeRange(long start, long stop) throws YamcsException {
        YarchDatabaseInstance db = YarchDatabase.getInstance(instance);
        Stream stream = db.getStream(streamName);
        if (stream != null && !stream.isClosed()) {
            closeExistingStream();
        }

        currentRequest.setRangeStart(start);
        currentRequest.setRangeStop(stop);
        for (ReplayHandler rh : handlers.values()) {
            rh.setRequest(currentRequest);
        }
    }

    private void closeExistingStream() {
        if (state != ReplayState.INITIALIZATION) {
            state = ReplayState.INITIALIZATION;
            YarchDatabaseInstance db = YarchDatabase.getInstance(instance);
            Stream s = db.getStream(streamName);
            if (s != null) {
                s.removeSubscriber(this);
                String query = "CLOSE STREAM " + streamName;
                log.debug("running query: {}", query);
                try {
                    db.executeDiscardingResult(query);
                } catch (StreamSqlException | ParseException e) {
                    throw new RuntimeException(e);
                }
            }

            // if paused, there is a tuple already emitted and ready to be processed in the onTuple method.
            // we want to get rid of it
            if (sleeping) {
                dropTuple = true;
                log.debug("Releasing semaphore");
                semaphore.release();
            }
        }
    }

    public void changeSpeed(SpeedSpec newSpeed) {
        log.debug("Changing speed to {}", newSpeed);
        currentRequest.setSpeed(newSpeed);
    }

    public void changeEndAction(EndAction endAction) {
        log.debug("Changing end action to {}", endAction);
        currentRequest.setEndAction(endAction);
    }

    public void pause() {
        state = ReplayState.PAUSED;
    }

    public synchronized void quit() {
        if (quitting) {
            return;
        }
        quitting = true;
        log.debug("Replay quitting");

        try {
            YarchDatabaseInstance db = YarchDatabase.getInstance(instance);
            if (db.getStream(streamName) != null) {
                db.execute("close stream " + streamName);
            }
        } catch (Exception e) {
            log.error("Exception while quitting", e);
        }
        replayServer.replayFinished();
    }

    @Override
    public void onTuple(Stream s, Tuple t) {
        if (quitting) {
            return;
        }
        long time = t.getTimestampColumn("gentime");

        try {
            sleepUntilTime(time);

            if (dropTuple) {
                dropTuple = false;
                return;
            }

            replayTime = time;

            ProtoDataType type = ProtoDataType.forNumber((Integer) t.getColumn(0));
            Object data = handlers.get(type).transform(t);
            if (data != null) {
                listener.newData(type, data);
            }
            lastDataSentTime = System.currentTimeMillis();
            lastDataTime = time;

            if (currentRequest.getSpeed().getType() == Type.STEP_BY_STEP) {
                // Force user to trigger next step.
                state = ReplayState.PAUSED;
                signalStateChange();
            }
        } catch (InterruptedException e) {
            if (!quitting) {
                log.warn("Interrupted: ", e);
                quit();
            }
        }
    }

    private void sleepUntilTime(long time) throws InterruptedException {
        long waitTime = 0;
        SpeedSpec speed = currentRequest.getSpeed();
        switch (speed.getType()) {
        case AFAP:
            break;
        case FIXED_DELAY:
            long ctime = System.currentTimeMillis();
            if (lastDataSentTime != -1) {
                waitTime = (long) (speed.getFixedDelay() - (ctime - lastDataSentTime));
            }
            break;
        case ORIGINAL:
            waitTime = (long) ((time - lastDataTime) / speed.getMultiplier());
            if (waitTime > MAX_WAIT_TIME) {
                waitTime = MAX_WAIT_TIME;
            }
            break;
        case STEP_BY_STEP:
            break;
        }

        if (waitTime > 0) {
            sleeping = true;

            double d = (time - lastDataTime) / (double) waitTime;

            // update the replay time every second
            while (true) {
                long sleepTime = Math.min(waitTime, 1000);

                if (semaphore.tryAcquire(sleepTime, TimeUnit.MILLISECONDS)) {
                    break;
                }
                if (state == ReplayState.PAUSED) {
                    continue;
                }
                waitTime -= sleepTime;
                if (waitTime > 0) {
                    replayTime += d * sleepTime;
                } else {
                    break;
                }
            }
        }
        sleeping = false;
    }

    @Override
    public synchronized void streamClosed(Stream stream) {
        if (ignoreClose) { // this happens when we close the stream to reopen
                           // another one
            ignoreClose = false;
            return;
        }
        if (quitting) {
            return;
        }

        if (currentRequest.getEndAction() == EndAction.QUIT) {
            state = ReplayState.CLOSED;
            signalStateChange();
            quit();
        } else if (currentRequest.getEndAction() == EndAction.STOP) {
            state = ReplayState.STOPPED;
            signalStateChange();
        } else if (currentRequest.getEndAction() == EndAction.LOOP) {
            if (stream.getDataCount() == 0) {
                state = ReplayState.STOPPED; // there is no data in this stream
                signalStateChange();
            } else {
                state = ReplayState.INITIALIZATION;
                currentRequest.setPlayFrom(currentRequest.getRangeStart());
                start();
            }
        }
    }

    private void signalStateChange() {
        try {
            if (quitting) {
                return;
            }
            ReplayStatus.Builder rsb = ReplayStatus.newBuilder().setState(state);
            if (state == ReplayState.ERROR) {
                rsb.setErrorMessage(errorString);
            }
            ReplayStatus rs = rsb.build();
            listener.stateChanged(rs);

        } catch (Exception e) {
            log.warn("got exception while signaling the state change: ", e);
        }
    }

    public ReplayOptions getCurrentReplayRequest() {
        return currentRequest;
    }

    public long getReplayTime() {
        return replayTime;
    }
}
