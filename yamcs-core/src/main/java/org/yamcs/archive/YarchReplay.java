package org.yamcs.archive;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.SpeedLimitStream;
import org.yamcs.yarch.SpeedSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.protobuf.MessageLite;

/**
 * Performs a replay from Yarch So far supported are: TM packets, PP groups, Events, Parameters and Command History.
 * 
 * It relies on handlers for each data type. 
 * Each handler creates a stream, the streams are merged and the output is sent to the listener 
 * This class can also handle
 *   pause/resume: simply stop sending data 
 *   seek: closes the streams and creates new ones with a different starting time.
 * 
 * @author nm
 *
 */
public class YarchReplay implements StreamSubscriber {
    ReplayServer replayServer;
    volatile String streamName;
    volatile boolean quitting=false;
    private volatile ReplayState state = ReplayState.INITIALIZATION;
    static Logger log=LoggerFactory.getLogger(YarchReplay.class.getName());
    private volatile String errorString="";
    int numPacketsSent;
    final String instance;
    static AtomicInteger counter=new AtomicInteger();
    XtceDb xtceDb;
   
    volatile ReplayRequest currentRequest;

    Map<ProtoDataType,ReplayHandler> handlers;
    
    private Semaphore pausedSemaphore=new Semaphore(0);
    boolean dropTuple=false; //set to true when jumping to a different time
    volatile boolean ignoreClose;
    ReplayListener listener;
    public YarchReplay(ReplayServer replayServer, ReplayRequest rr, ReplayListener listener,  XtceDb xtceDb, AuthenticationToken authToken)
            throws IOException, ConfigurationException,  YamcsException, YamcsApiException {
        this.listener = listener;
        this.replayServer=replayServer;
        this.xtceDb=xtceDb;
        this.instance=replayServer.instance;

        if (!rr.hasPacketRequest() && !rr.hasParameterRequest()
                        && !rr.hasEventRequest() && !rr.hasPpRequest()
                        && !rr.hasCommandHistoryRequest()) {
            throw new YamcsException("Empty replay request");
        }

        setRequest(rr, authToken);
      
    }


   

    public void setRequest(ReplayRequest newRequest, AuthenticationToken authToken) throws YamcsException {
        if(state!=ReplayState.INITIALIZATION && state!=ReplayState.STOPPED) {
            throw new YamcsException("changing the request only supported in the INITIALIZATION and STOPPED states");
        }
        
        //get the start/stop from utcStart/utcStop
        ReplayRequest.Builder b = ReplayRequest.newBuilder(newRequest);
        
        if(!newRequest.hasStart() && newRequest.hasUtcStart()) {
            b.setStart(TimeEncoding.parse(newRequest.getUtcStart()));
        }
        if(!newRequest.hasStop() && newRequest.hasUtcStop()) {
            b.setStop(TimeEncoding.parse(newRequest.getUtcStop()));
        }
        newRequest  = b.build();

        log.debug("Replay request for time: [{}, {}]",
                (newRequest.hasStart() ? TimeEncoding.toString(newRequest.getStart()) : null),
                (newRequest.hasStop() ? TimeEncoding.toString(newRequest.getStop()) : null));
        
        if (newRequest.hasStart() && newRequest.hasStop() && newRequest.getStart()>newRequest.getStop()) {
            log.warn("throwing new packetexception: stop time has to be greater than start time");
            throw new YamcsException("stop has to be greater than start");
        }

        currentRequest = newRequest;
        handlers=new HashMap<ProtoDataType,ReplayHandler>();
        
        if (currentRequest.hasParameterRequest()) {
            throw new YamcsException("The replay cannot handle directly parameters. Please create a replay processor for that");
        }
        
        
        if (currentRequest.hasEventRequest())
            handlers.put(ProtoDataType.EVENT, new EventReplayHandler());
        if (currentRequest.hasPacketRequest())
            handlers.put(ProtoDataType.TM_PACKET, new XtceTmReplayHandler(xtceDb));
        if (currentRequest.hasPpRequest())
            handlers.put(ProtoDataType.PP, new PpReplayHandler(xtceDb));
        if (currentRequest.hasCommandHistoryRequest())
            handlers.put(ProtoDataType.CMD_HISTORY, new CommandHistoryReplayHandler(instance));
        
        for(ReplayHandler rh:handlers.values()) {
            rh.setRequest(newRequest);
        }
    }

    public ReplayState getState() {
        return state;
    }

    public synchronized void start() {
        switch(state) {
        case RUNNING:
            log.warn("start called when already running, call ignored");
            return;
        case INITIALIZATION:
        case STOPPED:
            try {
                initReplay();
                state=ReplayState.RUNNING;
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Got exception when creating the stream: ", e);
                errorString=e.toString();
                state=ReplayState.ERROR;
            }
            break;
        case PAUSED:
            state=ReplayState.RUNNING;
            pausedSemaphore.release();
            break;
        case ERROR:
        case CLOSED:
            //do nothing?
        }
    }

    private void initReplay() throws StreamSqlException, ParseException {
        streamName="replay_stream"+counter.incrementAndGet();

        StringBuilder sb=new StringBuilder();
        sb.append("CREATE STREAM "+streamName+" AS ");
        
        if(handlers.size()>1){
            sb.append("MERGE ");
        }

        boolean first=true;
        for(ReplayHandler rh:handlers.values()) {
            String selectCmd=rh.getSelectCmd();
            if(selectCmd!=null) {
                if(first) first=false;
                else sb.append(", ");
                if(handlers.size()>1) sb.append("(");
                sb.append(selectCmd);
                if(handlers.size()>1) sb.append(")");
            }
        }
        
        if(first) {
            if(currentRequest.getEndAction()==EndAction.QUIT) {
                signalStateChange();
            }
            return;
        }

        if(handlers.size()>1){
            sb.append(" USING gentime");
        }
        ReplaySpeed rs;
        if(currentRequest.hasSpeed()) {
            rs = currentRequest.getSpeed();
        } else {
            rs = ReplaySpeed.newBuilder().setType(ReplaySpeedType.REALTIME).setParam(1).build();
        }
        switch(rs.getType()) {
        case    AFAP:
            sb.append(" SPEED AFAP");
            break;
        case FIXED_DELAY:
            sb.append(" SPEED FIXED_DELAY "+(long)rs.getParam());
            break;
        case REALTIME:
            sb.append(" SPEED ORIGINAL gentime,"+(long)rs.getParam());
        }
        if(handlers.size()>1 && currentRequest.hasReverse() && currentRequest.getReverse()) {
            sb.append(" ORDER DESC");
        }

        String query=sb.toString();
        log.debug("running query "+query);
        YarchDatabase ydb=YarchDatabase.getInstance(instance);
        ydb.execute(query);
        Stream s=ydb.getStream(streamName);
        s.addSubscriber(this);
        numPacketsSent=0;
        s.start();
    }
    
    public void seek(long newReplayTime) throws YamcsException {
        if(state!=ReplayState.INITIALIZATION) {
            if(state==ReplayState.PAUSED) {
                dropTuple=true;
                pausedSemaphore.release();
            }
            state=ReplayState.INITIALIZATION;
            String query="CLOSE STREAM "+streamName;
            ignoreClose=true;
            try {
                YarchDatabase db=YarchDatabase.getInstance(instance);
                if(db.getStream(streamName)!=null) {
                    log.debug("running query: "+query);
                    db.execute(query);
                } else {
                    log.debug("Stream already closed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Got exception when closing the stream: ", e);
                errorString=e.toString();
                state=ReplayState.ERROR;
                signalStateChange();
            }
        }
        currentRequest=ReplayRequest.newBuilder(currentRequest).setStart(newReplayTime).build();
        for(ReplayHandler rh:handlers.values()) {
            rh.setRequest(currentRequest);
        }
        start();
    }
    public void changeSpeed(ReplaySpeed newSpeed) {
        log.debug("Changing speed to {}", newSpeed);
        
        YarchDatabase ydb=YarchDatabase.getInstance(instance);
        Stream s=ydb.getStream(streamName);
        if(!(s instanceof SpeedLimitStream)) {
            throw new RuntimeException("Cannot change speed on a "+s.getClass()+" stream");
        } else {
            ((SpeedLimitStream)s).setSpeedSpec(toSpeedSpec(newSpeed));
        }
        ReplayRequest.Builder b = ReplayRequest.newBuilder(currentRequest);
        b.setSpeed(newSpeed);
        currentRequest = b.build(); 
        
    }

    private SpeedSpec toSpeedSpec(ReplaySpeed speed) {
        SpeedSpec ss;
        switch(speed.getType()) {
        case  AFAP: 
            ss=new SpeedSpec(SpeedSpec.Type.AFAP);
            break;
        case FIXED_DELAY:            
            ss=new SpeedSpec(SpeedSpec.Type.FIXED_DELAY, (int) speed.getParam());
            break;
        case REALTIME:
            ss=new SpeedSpec(SpeedSpec.Type.ORIGINAL, "gentime", speed.getParam());
            break;
        default:
            throw new RuntimeException("Unkown speed type "+speed.getType());                
        }
        return ss;
    }
    
    public void pause() {
        state=ReplayState.PAUSED;
    }

    public synchronized void quit() {
        if(quitting) return;
        quitting=true;
        log.debug("Replay quitting");
        
        this.notify();
        try {
            YarchDatabase db=YarchDatabase.getInstance(instance);
            if(db.getStream(streamName)!=null) db.execute("close stream "+streamName);
        } catch (Exception e) {
            log.error( "Exception whilst quitting", e );
        };
        replayServer.replayFinished();
    }


    @Override
    public void onTuple(Stream s, Tuple t) {
        if(quitting) return;
        try {
            while(state==ReplayState.PAUSED) {
                pausedSemaphore.acquire();
            }
            if(dropTuple) {
                dropTuple=false;
                return;
            }
            ProtoDataType type=ProtoDataType.valueOf((Integer)t.getColumn(0));
            MessageLite data = handlers.get(type).transform(t);
            
            if(data!=null) {
                listener.newData(type, data);
            }
                
        } catch (Exception e) {
            if(!quitting) {
                log.warn("Exception received: ", e);
                e.printStackTrace();
                quit();
            }
        }
    }

    @Override
    public synchronized void streamClosed(Stream stream) {
        for(ReplayHandler rh:handlers.values()) {
            rh.reset();
        }
        
        if(ignoreClose) { //this happens when we close the stream to reopen another one
            ignoreClose=false;
            return;
        }
        
        if(currentRequest.getEndAction()==EndAction.QUIT) {
        	state=ReplayState.CLOSED;
        	signalStateChange();
        	quit();
        } else if(currentRequest.getEndAction()==EndAction.STOP) {
        	state=ReplayState.STOPPED;
        	signalStateChange();
        } else if(currentRequest.getEndAction()==EndAction.LOOP) {
        	if(numPacketsSent==0) {
        		state=ReplayState.STOPPED; //there is no data in this stream
        		signalStateChange();
        	} else {
        		state=ReplayState.INITIALIZATION;
        		start();
        	}
        }
    }

    private void signalStateChange() {
        try {
            if(quitting) return;
            ReplayStatus.Builder rsb=ReplayStatus.newBuilder().setState(state);
            if(state==ReplayState.ERROR) rsb.setErrorMessage(errorString);
            ReplayStatus rs=rsb.build();
            listener.stateChanged(rs);
            
        } catch (Exception e) {
            log.warn("got exception while signaling the state change: ", e);
        }
    }

    public ReplayRequest getCurrentReplayRequest() {
        return currentRequest;
    }
}
