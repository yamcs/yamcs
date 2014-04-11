package org.yamcs.tctm;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.TmProcessor;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.archive.PacketWithTime;

import com.google.common.util.concurrent.AbstractService;

import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.hornetq.StreamAdapter;


/**
 * receives data from HornetQ and publishes it into a yamcs stream
 * 
 * @author nm
 *
 */
public class HornetQTmProvider extends  AbstractService implements TmPacketProvider, MessageHandler {
	protected volatile long packetcount = 0;
	protected volatile boolean disabled=false;

	protected Logger log=LoggerFactory.getLogger(this.getClass().getName());
    private TmProcessor tmProcessor;
    YamcsSession yamcsSession; 
    final private YamcsClient msgClient;
    
    
	public HornetQTmProvider(String instance, String name, String hornetAddress) throws ConfigurationException  {
        SimpleString queue=new SimpleString(hornetAddress+"-HornetQTmProvider");
        
        try {
            yamcsSession=YamcsSession.newBuilder().build();
            msgClient=yamcsSession.newClientBuilder().setDataProducer(false).setDataConsumer(new SimpleString(hornetAddress), queue).
                setFilter(new SimpleString(StreamAdapter.UNIQUEID_HDR_NAME+"<>"+StreamAdapter.UNIQUEID)).
                build();
           
        } catch (Exception e) {
            throw new ConfigurationException(e.getMessage(),e);
        }
	}
	
	
	@Override
	public void setTmProcessor(TmProcessor tmProcessor) {
	    this.tmProcessor=tmProcessor;
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
            PacketWithTime pwt =  new PacketWithTime(TimeEncoding.currentInstant(), tm.getGenerationTime(), tm.getPacket().toByteArray());
            tmProcessor.processPacket(pwt);
        } catch(YamcsApiException e){
            log.warn( "{} for message: {}", e.getMessage(), msg);
        }
    }

    @Override
    protected void doStart() {
        try {
            msgClient.dataConsumer.setMessageHandler(this);
            notifyStarted();
        } catch (HornetQException e) {
            log.error("Failed to set message handler");
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        try {
            msgClient.close();
            notifyStopped();
        } catch (HornetQException e) {
            log.error("Got exception when quiting:", e);
            notifyFailed(e);
        }
    }

}

