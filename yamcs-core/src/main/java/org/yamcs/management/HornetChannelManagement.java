package org.yamcs.management;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ChannelListener;
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
import org.yamcs.protobuf.YamcsManagement.ChannelInfo;
import org.yamcs.protobuf.YamcsManagement.ChannelRequest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.protobuf.YamcsManagement.TmStatistics;

/**
 * provides channel management services via hornetq
 * @author nm
 *
 */
public class HornetChannelManagement implements ChannelListener {
    YamcsSession ysession;
    YamcsClient yclient, channelControlServer, linkControlServer;
    static Logger log=LoggerFactory.getLogger(HornetChannelManagement.class.getName());
    Map<Channel, Statistics> channels=new ConcurrentHashMap<Channel, Statistics>();
    ManagementService mservice;
    
    static Statistics STATS_NULL=Statistics.newBuilder().setInstance("null").setChannelName("null").build();//we use this one because ConcurrentHashMap does not support null values
    
    
    public HornetChannelManagement(ManagementService mservice, ScheduledThreadPoolExecutor timer) throws YamcsApiException, HornetQException {
        this.mservice=mservice;
        timer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {updateStatistics();}
        }, 1, 1, TimeUnit.SECONDS);

        
        if(ysession!=null) return;
        ysession=YamcsSession.newBuilder().build();
        
        //trick to make sure that the channel info queue exists
        yclient=ysession.newClientBuilder().setDataConsumer(CHANNEL_INFO_ADDRESS, CHANNEL_INFO_ADDRESS).build();
        yclient.close();
        
        yclient=ysession.newClientBuilder().setDataProducer(true).build();
        channelControlServer=ysession.newClientBuilder().setRpcAddress(CHANNEL_CONTROL_ADDRESS).build();
        channelControlServer.rpcConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage message) {
                try {
                    processChannelControlMessage(message);
                } catch (Exception e) {
                    log.error("Error when processing request");
                    e.printStackTrace();
                }
            }
        });
    }
    
    
    
    private void processChannelControlMessage(ClientMessage msg) throws YamcsApiException, HornetQException {
        Privilege priv = HornetQAuthPrivilege.getInstance(msg);
        
        SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
        if(replyto==null) {
            log.warn("Did not receive a replyto header. Ignoring the request");
            return;
        }
        try {
            String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
            log.debug("Received a new request: "+req);
            if("createChannel".equalsIgnoreCase(req)) {
                ChannelRequest cr=(ChannelRequest)Protocol.decode(msg, ChannelRequest.newBuilder());
                mservice.createChannel(cr, priv);
                channelControlServer.sendReply(replyto, "OK", null);
            } else if("connectToChannel".equalsIgnoreCase(req)) {
                ChannelRequest cr=(ChannelRequest)Protocol.decode(msg, ChannelRequest.newBuilder());
                mservice.connectToChannel(cr, priv);
                channelControlServer.sendReply(replyto, "OK", null);
            } else if("pauseReplay".equalsIgnoreCase(req)) {
                ChannelRequest cr=(ChannelRequest)Protocol.decode(msg, ChannelRequest.newBuilder());
                mservice.connectToChannel(cr, priv);
                Channel c=Channel.getInstance(cr.getInstance(), cr.getName());
                c.pause();
                channelControlServer.sendReply(replyto, "OK", null);
            } else if("resumeReplay".equalsIgnoreCase(req)) {
                ChannelRequest cr=(ChannelRequest)Protocol.decode(msg, ChannelRequest.newBuilder());
                Channel c=Channel.getInstance(cr.getInstance(), cr.getName());
                c.resume();
                channelControlServer.sendReply(replyto, "OK", null);
            } else if("seekReplay".equalsIgnoreCase(req)) {
                ChannelRequest cr=(ChannelRequest)Protocol.decode(msg, ChannelRequest.newBuilder());
                Channel c=Channel.getInstance(cr.getInstance(), cr.getName());
                if(!cr.hasSeekTime()) throw new YamcsException("seekReplay requested without a seektime");
                c.seek(cr.getSeekTime());
                channelControlServer.sendReply(replyto, "OK", null);
            } else  {
                throw new YamcsException("Unknown request '"+req+"'");
            }
        } catch (YamcsException e) {
            log.warn("Sending error reply "+ e);
            channelControlServer.sendErrorReply(replyto, e.getMessage());
        } 
    }

   
    @Override
    public void channelAdded(Channel channel) {
        try {
            ChannelInfo ci=getChannelInfo(channel);
            sendChannelEvent("channelUpdated", ci, false);
            channels.put(channel, STATS_NULL);
        } catch (Exception e) {
            log.error("Exception when registering channel: ", e);
        }
    }
    
    
    @Override
    public void channelClosed(Channel channel) {
        ChannelInfo ci=getChannelInfo(channel);
        sendChannelEvent("channelClosed", ci, true);
        channels.remove(channel);
    }

    @Override
    public void channelStateChanged(Channel channel) {
        try {
            ChannelInfo ci=getChannelInfo(channel);
            sendChannelEvent("channelUpdated", ci, false);
        } catch (Exception e) {
            log.error("Exception when sending channelPaused event: ", e);
        }
    }


    private void sendChannelEvent(String eventName, ChannelInfo ci, boolean expire) {
        ClientMessage msg=ysession.session.createMessage(false);
        String lvn=ci.getInstance()+"."+ci.getName();
        msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
        msg.putStringProperty(HDR_EVENT_NAME, eventName);
        if(expire) {
            msg.setExpiration(System.currentTimeMillis()+5000);
        }
        Protocol.encode(msg, ci);
        try {
            yclient.dataProducer.send(CHANNEL_INFO_ADDRESS, msg);
        } catch (HornetQException e) {
            log.error("Exception when sending channel event: ", e);
        }
    }
    
    
    public void updateStatistics() {
        try {
            for(Entry<Channel,Statistics> entry:channels.entrySet()) {
                Channel channel=entry.getKey();
                Statistics stats=entry.getValue();
                ProcessingStatistics ps=channel.getTmProcessor().getStatistics();
                if((stats==STATS_NULL) || (ps.getLastUpdated()>stats.getLastUpdated())) {
                    stats=buildStats(channel);
                    channels.put(channel, stats);
                }
                if(stats!=STATS_NULL) {
                    sendChannelStatistics(channel, stats);
                }
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private Statistics buildStats(Channel channel) {
        ProcessingStatistics ps=channel.getTmProcessor().getStatistics();
        Statistics.Builder statsb=Statistics.newBuilder();
        statsb.setLastUpdated(ps.getLastUpdated());
        statsb.setInstance(channel.getInstance()).setChannelName(channel.getName());
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
    
    private void sendChannelStatistics(Channel channel, Statistics stats) {
        try {
            ClientMessage msg=ysession.session.createMessage(false);
            String lvn=channel.getInstance()+"."+channel.getName();
            msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
            msg.setExpiration(System.currentTimeMillis()+2000);
            Protocol.encode(msg, stats);
            yclient.dataProducer.send(CHANNEL_STATISTICS_ADDRESS, msg);
        } catch (HornetQException e){
            log.error("got exception when sending the channel processing statistics: ", e);
        }
    }

    private ChannelInfo getChannelInfo(Channel channel) {
        ChannelInfo.Builder cib=ChannelInfo.newBuilder().setInstance(channel.getInstance())
        .setName(channel.getName()).setType(channel.getType())
        .setCreator(channel.getCreator()).setHasCommanding(channel.hasCommanding())
        .setState(channel.getState());
        
        if(channel.isReplay()) {
            cib.setReplayRequest(channel.getReplayRequest());
            cib.setReplayState(channel.getReplayState());
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
            yclient.dataProducer.send(CHANNEL_INFO_ADDRESS, msg);
        } catch (HornetQException e) {
           log.error("exception when sedning client event: ", e);
        }
    }

}
