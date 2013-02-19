package org.yamcs.archive;

import static org.yamcs.api.Protocol.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ppdb.PpDefDb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;

import com.google.protobuf.MessageLite;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Instant;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.xtce.XtceDb;

/**
 * Performs a replay from Yarch to HornetQ. So far supported are: TM packets, PP groups, Events, Parameters and Command History.
 * 
 * It relies on handlers for each data type. 
 * Each handler creates a stream, the streams are merged and the output sent via HornetQ. 
 * This class can also handle
 *   pause/resume: simply stop sending data 
 *   seek: closes the streams and creates new ones with a different starting time.
 * 
 * @author nm
 *
 */
class YarchReplay implements StreamSubscriber, Runnable {
    ReplayServer replayServer;
    volatile String streamName;
    volatile boolean quitting=false;
    private volatile ReplayState state=ReplayState.INITIALIZATION;
    static Logger log=LoggerFactory.getLogger(YarchReplay.class.getName());
    private volatile String errorString="";
    int numPacketsSent;
    final String instance;
    static AtomicInteger counter=new AtomicInteger();
    PpDefDb ppdb;
    XtceDb xtceDb;
    
    YamcsSession ysession;
    YamcsClient yclient;
    ReplayRequest currentRequest;

    Map<ProtoDataType,ReplayHandler> handlers;
    SimpleString dataAddress;
    private Semaphore pausedSemaphore=new Semaphore(0);
    boolean dropTuple=false; //set to true when jumping to a different time
    volatile boolean ignoreClose;
    
    public YarchReplay(ReplayServer replayServer, ReplayRequest rr,  SimpleString dataAddress, PpDefDb ppdb, XtceDb xtceDb) throws IOException, ConfigurationException, HornetQException, YamcsException, YamcsApiException {
        this.replayServer=replayServer;
        this.dataAddress=dataAddress;
        this.ppdb=ppdb;
        this.xtceDb=xtceDb;
        this.instance=replayServer.instance;

        if(rr.getTypeList().isEmpty()) throw new YamcsException("no replay data types specified");
        setRequest(rr);
        ysession=YamcsSession.newBuilder().build();
        yclient=ysession.newClientBuilder().setRpc(true).setDataProducer(true).build();
        Protocol.killProducerOnConsumerClosed(yclient.dataProducer, dataAddress);
    }


    @Override
    public void run() {
        try {
            while(!quitting) {
                ClientMessage msg=yclient.rpcConsumer.receive();
                if(msg==null) {
                    if(!quitting) log.warn("null message received from the control queue");
                    continue;
                }
                SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
                if(replyto==null) {
                    log.warn("did not receive a replyto header. Ignoring the request");
                    continue;
                }
                try {
                    String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
                    log.debug("received a new request: "+req);
                    if("Start".equalsIgnoreCase(req)) {
                        start();
                        yclient.sendReply(replyto, "OK", null);
                    } else if("GetReplayStatus".equalsIgnoreCase(req)) {
                        ReplayStatus status=ReplayStatus.newBuilder().setState(state).build();
                        yclient.sendReply(replyto, "REPLY_STATUS", status);
                    } else if("Pause".equalsIgnoreCase(req)){
                        pause();
                        yclient.sendReply(replyto, "OK", null);
                    } else if("Resume".equalsIgnoreCase(req)){
                        start();
                        yclient.sendReply(replyto, "OK", null);
                    } else if("Quit".equalsIgnoreCase(req)){
                        quit();
                        yclient.sendReply(replyto, "OK", null);
                    } else if("Seek".equalsIgnoreCase(req)){
                        Instant inst=(Instant) decode(msg, Instant.newBuilder());
                        seek(inst.getInstant());
                        yclient.sendReply(replyto, "OK", null);
                    } else if("ChangeReplayRequest".equalsIgnoreCase(req)){
                        setRequest((ReplayRequest)decode(msg, ReplayRequest.newBuilder()));
                        yclient.sendReply(replyto, "OK", null);
                    } else  {
                        throw new YamcsException("Unknown request '"+req+"'");
                    }
                } catch (YamcsException e) {
                    log.warn("sending error reply ", e);
                    yclient.sendErrorReply(replyto, e.getMessage());

                }
            }
        } catch (Exception e) {
            log.warn("caught exception in packet reply: ", e);
            e.printStackTrace();
        }
    }

    private void setRequest(ReplayRequest newRequest) throws YamcsException {
        if(state!=ReplayState.INITIALIZATION && state!=ReplayState.STOPPED) {
            throw new YamcsException("changing the request only supported in the INITIALIZATION and STOPPED states");
        }
        
        if (newRequest.getStart()>newRequest.getStop()) {
            log.warn("throwing new packetexception: stop time has to be grater than start time");
            throw new YamcsException("stop has to be greater than start");
        }

        currentRequest=newRequest;
        handlers=new HashMap<ProtoDataType,ReplayHandler>();
        for(ProtoDataType rdp:currentRequest.getTypeList()) {
            switch(rdp) {
            case EVENT:
                handlers.put(rdp, new EventReplayHandler());
                break;
            case TM_PACKET:
                handlers.put(rdp, new XtceTmReplayHandler(xtceDb));
                break;
            case PP:
                handlers.put(rdp, new PpReplayHandler(ppdb));
                break;
            case PARAMETER:
                handlers.put(rdp, new ParameterReplayHandler(instance, xtceDb, ppdb));
                break;
            case CMD_HISTORY:
                handlers.put(rdp, new CommandHistoryReplayHandler(instance));
            }
        }
        
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
        switch(currentRequest.getSpeed().getType()) {
        case    AFAP:
            sb.append(" SPEED AFAP");
            break;
        case FIXED_DELAY:
            sb.append(" SPEED FIXED_DELAY "+(long)currentRequest.getSpeed().getParam());
            break;
        case REALTIME:
            sb.append(" SPEED ORIGINAL gentime,"+(long)currentRequest.getSpeed().getParam());
        }

        String query=sb.toString();
        log.debug("running query "+query);
        YarchDatabase db=YarchDatabase.getInstance(instance);
        db.execute(query);
        Stream s=db.getStream(streamName);
        s.addSubscriber(this);
        numPacketsSent=0;
        s.start();
    }
    
    private void seek(long newReplayTime) throws YamcsException {
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
                log.debug("running query: "+query);
                db.execute(query);
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

    private void pause() {
        state=ReplayState.PAUSED;
    }

    public synchronized void quit() {
        if(quitting) return;
        quitting=true;
        log.debug("Replay quitting");
        
        this.notify();
        try {
            yclient.close();
            ysession.close();
        } catch (HornetQException e) {
            log.warn("Got exception when quitting: ", e);
        }
        try {
            YarchDatabase db=YarchDatabase.getInstance(instance);
            if(db.getStream(streamName)!=null) db.execute("close stream "+streamName);
        } catch (Exception e) {
            e.printStackTrace();
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
            if(data!=null)
                yclient.sendData(dataAddress, type, data);
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
            yclient.sendData(dataAddress, ProtoDataType.STATE_CHANGE, rs);
        } catch (Exception e) {
            log.warn("got exception while signaling the sate change: ", e);
        }
    }
}
