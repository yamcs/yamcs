package org.yamcs.management;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorListener;
import org.yamcs.HornetQAuthPrivilege;
import org.yamcs.Privilege;
import org.yamcs.xtceproc.ProcessingStatistics;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.Message;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;

import static org.yamcs.api.Protocol.*;

import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.YamcsManagement.YProcessorRequest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.protobuf.YamcsManagement.TmStatistics;
import org.yamcs.protobuf.YamcsManagement.YProcessorInfo;

/**
 * provides yprocessor management services via hornetq
 * @author nm
 *
 */
public class HornetYProcessorManagement implements YProcessorListener {
    YamcsSession ysession;
    YamcsClient yclient, yprocControlServer, linkControlServer;
    static Logger log=LoggerFactory.getLogger(HornetYProcessorManagement.class.getName());
    Map<YProcessor, Statistics> yprocs=new ConcurrentHashMap<YProcessor, Statistics>();
    ManagementService mservice;
    
    static Statistics STATS_NULL=Statistics.newBuilder().setInstance("null").setYProcessorName("null").build();//we use this one because ConcurrentHashMap does not support null values
    
    
    public HornetYProcessorManagement(ManagementService mservice, ScheduledThreadPoolExecutor timer) throws YamcsApiException, HornetQException {
        this.mservice=mservice;
        timer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {updateStatistics();}
        }, 1, 1, TimeUnit.SECONDS);

        
        if(ysession!=null) return;
        ysession=YamcsSession.newBuilder().build();
        
        //trick to make sure that the yprocessor info queue exists
        yclient=ysession.newClientBuilder().setDataConsumer(YPROCESSOR_INFO_ADDRESS, YPROCESSOR_INFO_ADDRESS).build();
        yclient.close();
        
        yclient=ysession.newClientBuilder().setDataProducer(true).build();
        yprocControlServer=ysession.newClientBuilder().setRpcAddress(YPROCESSOR_CONTROL_ADDRESS).build();
        yprocControlServer.rpcConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage message) {
                try {
                    processYProcControlMessage(message);
                } catch (Exception e) {
                    log.error("Error when processing request");
                    e.printStackTrace();
                }
            }
        });
    }
    
    
    
    private void processYProcControlMessage(ClientMessage msg) throws YamcsApiException, HornetQException {
        Privilege priv = HornetQAuthPrivilege.getInstance(msg);
        
        SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
        if(replyto==null) {
            log.warn("Did not receive a replyto header. Ignoring the request");
            return;
        }
        try {
            String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
            log.debug("Received a new request: "+req);
            if("createYProcessor".equalsIgnoreCase(req)) {
                YProcessorRequest cr=(YProcessorRequest)Protocol.decode(msg, YProcessorRequest.newBuilder());
                mservice.createYProcessor(cr, priv);
                yprocControlServer.sendReply(replyto, "OK", null);
            } else if("connectToYProcessor".equalsIgnoreCase(req)) {
                YProcessorRequest cr=(YProcessorRequest)Protocol.decode(msg, YProcessorRequest.newBuilder());
                mservice.connectToYProcessor(cr, priv);
                yprocControlServer.sendReply(replyto, "OK", null);
            } else if("pauseReplay".equalsIgnoreCase(req)) {
                YProcessorRequest cr=(YProcessorRequest)Protocol.decode(msg, YProcessorRequest.newBuilder());
                mservice.connectToYProcessor(cr, priv);
                YProcessor c=YProcessor.getInstance(cr.getInstance(), cr.getName());
                c.pause();
                yprocControlServer.sendReply(replyto, "OK", null);
            } else if("resumeReplay".equalsIgnoreCase(req)) {
                YProcessorRequest cr=(YProcessorRequest)Protocol.decode(msg, YProcessorRequest.newBuilder());
                YProcessor c=YProcessor.getInstance(cr.getInstance(), cr.getName());
                c.resume();
                yprocControlServer.sendReply(replyto, "OK", null);
            } else if("seekReplay".equalsIgnoreCase(req)) {
                YProcessorRequest cr=(YProcessorRequest)Protocol.decode(msg, YProcessorRequest.newBuilder());
                YProcessor c=YProcessor.getInstance(cr.getInstance(), cr.getName());
                if(!cr.hasSeekTime()) throw new YamcsException("seekReplay requested without a seektime");
                c.seek(cr.getSeekTime());
                yprocControlServer.sendReply(replyto, "OK", null);
            } else  {
                throw new YamcsException("Unknown request '"+req+"'");
            }
        } catch (YamcsException e) {
            log.warn("Sending error reply "+ e);
            yprocControlServer.sendErrorReply(replyto, e.getMessage());
        } 
    }

   
    @Override
    public void yProcessorAdded(YProcessor yproc) {
        try {
            YProcessorInfo ci=getYProcInfo(yproc);
            sendYProcEvent("yprocUpdated", ci, false);
            yprocs.put(yproc, STATS_NULL);
        } catch (Exception e) {
            log.error("Exception when registering yproc: ", e);
        }
    }
    
    
    @Override
    public void yProcessorClosed(YProcessor yprocl) {
        YProcessorInfo ci=getYProcInfo(yprocl);
        sendYProcEvent("yprocClosed", ci, true);
        yprocs.remove(yprocl);
    }

    @Override
    public void yProcessorStateChanged(YProcessor yproc) {
        try {
            YProcessorInfo ci=getYProcInfo(yproc);
            sendYProcEvent("yprocUpdated", ci, false);
        } catch (Exception e) {
            log.error("Exception when sending yprocUpdated event: ", e);
        }
    }


    private void sendYProcEvent(String eventName, YProcessorInfo ci, boolean expire) {
        ClientMessage msg=ysession.session.createMessage(false);
        String lvn=ci.getInstance()+"."+ci.getName();
        msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
        msg.putStringProperty(HDR_EVENT_NAME, eventName);
        if(expire) {
            msg.setExpiration(System.currentTimeMillis()+5000);
        }
        Protocol.encode(msg, ci);
        try {
            yclient.dataProducer.send(YPROCESSOR_INFO_ADDRESS, msg);
        } catch (HornetQException e) {
            log.error("Exception when sending yproc event: ", e);
        }
    }
    
    
    public void updateStatistics() {
        try {
            for(Entry<YProcessor,Statistics> entry:yprocs.entrySet()) {
                YProcessor yproc=entry.getKey();
                Statistics stats=entry.getValue();
                ProcessingStatistics ps=yproc.getTmProcessor().getStatistics();
                if((stats==STATS_NULL) || (ps.getLastUpdated()>stats.getLastUpdated())) {
                    stats=buildStats(yproc);
                    yprocs.put(yproc, stats);
                }
                if(stats!=STATS_NULL) {
                    sendYProcStatistics(yproc, stats);
                }
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private Statistics buildStats(YProcessor yproc) {
        ProcessingStatistics ps=yproc.getTmProcessor().getStatistics();
        Statistics.Builder statsb=Statistics.newBuilder();
        statsb.setLastUpdated(ps.getLastUpdated());
        statsb.setInstance(yproc.getInstance()).setYProcessorName(yproc.getName());
        Collection<ProcessingStatistics.TmStats> tmstats=ps.stats.values();
        if(tmstats==null) {
            return STATS_NULL;
        }
        
        for(ProcessingStatistics.TmStats t:tmstats) {
            TmStatistics ts=TmStatistics.newBuilder()
                .setPacketName(t.packetName).setLastPacketTime(t.lastPacketTime)
                .setLastReceived(t.lastReceived).setReceivedPackets(t.receivedPackets)
                .setSubscribedParameterCount(t.subscribedParameterCount).build();
            statsb.addTmstats(ts);
        }
        return statsb.build();
    }
    
    private void sendYProcStatistics(YProcessor yproc, Statistics stats) {
        try {
            ClientMessage msg=ysession.session.createMessage(false);
            String lvn=yproc.getInstance()+"."+yproc.getName();
            msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
            msg.setExpiration(System.currentTimeMillis()+2000);
            Protocol.encode(msg, stats);
            yclient.dataProducer.send(YPROCESSOR_STATISTICS_ADDRESS, msg);
        } catch (HornetQException e){
            log.error("got exception when sending the yproc processing statistics: ", e);
        }
    }

    private YProcessorInfo getYProcInfo(YProcessor yproc) {
        YProcessorInfo.Builder cib=YProcessorInfo.newBuilder().setInstance(yproc.getInstance())
        .setName(yproc.getName()).setType(yproc.getType())
        .setCreator(yproc.getCreator()).setHasCommanding(yproc.hasCommanding())
        .setState(yproc.getState());
        
        if(yproc.isReplay()) {
            cib.setReplayRequest(yproc.getReplayRequest());
            cib.setReplayState(yproc.getReplayState());
        }
        return cib.build();
    }
    
    
    public void registerClient(ClientInfo ci) {
        sendClientEvent("clientUpdated", ci, false);
    }

    
    public void unregisterClient(ClientInfo ci) {
        sendClientEvent("clientDisconnected", ci, true);
    }
    
    public void clientInfoChanged(ClientInfo ci) {
        sendClientEvent("clientUpdated", ci, false);
    }
    
    static int x=0;
    private void sendClientEvent(String eventName, ClientInfo ci, boolean expire){
        ClientMessage msg=ysession.session.createMessage(false);
        String lvn="Client "+ci.getId();
        x++;
        msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
        msg.putStringProperty(HDR_EVENT_NAME, eventName);
        if(expire) {
            msg.setExpiration(System.currentTimeMillis()+5000);
        }
        Protocol.encode(msg, ci);
        try {
            yclient.dataProducer.send(YPROCESSOR_INFO_ADDRESS, msg);
        } catch (HornetQException e) {
           log.error("exception when sedning client event: ", e);
        }
    }



    public void close() {
	try {
	    ysession.close();
	} catch (HornetQException e) {
	    log.error("Failed to close the yamcs session", e);
	}
    }

}
