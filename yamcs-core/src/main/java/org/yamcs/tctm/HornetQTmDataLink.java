package org.yamcs.tctm;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YamcsServer;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.archive.PacketWithTime;

import com.google.common.util.concurrent.AbstractService;

import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.time.TimeService;
import org.yamcs.hornetq.StreamAdapter;


/**
 * receives data from ActiveMQ and publishes it into a yamcs stream
 * 
 * @author nm
 *
 */
public class HornetQTmDataLink extends  AbstractService implements TmPacketDataLink, MessageHandler {
    protected volatile long packetcount = 0;
    protected volatile boolean disabled=false;

    protected Logger log=LoggerFactory.getLogger(this.getClass().getName());
    private TmSink tmSink;
    YamcsSession yamcsSession; 
    final private YamcsClient msgClient;
    final TimeService timeService;

    public HornetQTmDataLink(String instance, String name, String hornetAddress) throws ConfigurationException  {
        SimpleString queue = new SimpleString(hornetAddress+"-ActiveMQTmProvider");

        try {
            yamcsSession=YamcsSession.newBuilder().build();
            msgClient=yamcsSession.newClientBuilder().setDataProducer(false).setDataConsumer(new SimpleString(hornetAddress), queue).
                    setFilter(new SimpleString(StreamAdapter.UNIQUEID_HDR_NAME+"<>"+StreamAdapter.UNIQUEID)).
                    build();

        } catch (Exception e) {
            throw new ConfigurationException(e.getMessage(),e);
        }
        timeService = YamcsServer.getTimeService(instance);
    }


    @Override
    public void setTmSink(TmSink tmProcessor) {
        this.tmSink=tmProcessor;
    }

    @Override
    public boolean isArchiveReplay() {
        return false;
    }

    @Override
    public String getLinkStatus() {
        if (disabled) {
            return "DISABLED";
        } else {
            return "OK";
        }
    }

    @Override
    public void disable() {
        disabled=true;
    }

    @Override
    public void enable() {
        disabled=false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public String getDetailedStatus() {
        if(disabled) {
            return "DISABLED";
        } else {
            return "OK";
        }
    }

    @Override
    public long getDataCount() {
        return packetcount;
    }


    @Override
    public void onMessage(ClientMessage msg) {
        if(disabled) return;
        try {
            TmPacketData tm=(TmPacketData)Protocol.decode(msg, TmPacketData.newBuilder());
            packetcount++;
            //System.out.println("mark 1: message received: "+msg);
            PacketWithTime pwt =  new PacketWithTime(timeService.getMissionTime(), tm.getGenerationTime(), tm.getPacket().toByteArray());
            tmSink.processPacket(pwt);
        } catch(YamcsApiException e){
            log.warn( "{} for message: {}", e.getMessage(), msg);
        }
    }

    @Override
    protected void doStart() {
        try {
            msgClient.dataConsumer.setMessageHandler(this);
            notifyStarted();
        } catch (ActiveMQException e) {
            log.error("Failed to set message handler");
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        try {
            msgClient.close();
            notifyStopped();
        } catch (ActiveMQException e) {
            log.error("Got exception when quiting:", e);
            notifyFailed(e);
        }
    }

}

