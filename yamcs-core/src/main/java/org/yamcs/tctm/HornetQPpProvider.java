package org.yamcs.tctm;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ParameterValue;
import org.yamcs.ProcessedParameterDefinition;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;

import com.google.common.util.concurrent.AbstractService;

import org.yamcs.ppdb.PpDbFactory;
import org.yamcs.ppdb.PpDefDb;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.hornetq.StreamAdapter;


/**
 * receives data from HornetQ and publishes it into a yamcs stream
 * 
 * @author nm
 *
 */
public class HornetQPpProvider extends  AbstractService implements PpProvider, MessageHandler {
	protected volatile long packetcount = 0;
	protected volatile boolean disabled=false;

	protected Logger log=LoggerFactory.getLogger(this.getClass().getName());
    private PpListener ppListener;
    YamcsSession yamcsSession; 
    final private YamcsClient msgClient;
    final PpDefDb ppdb;
    
	public HornetQPpProvider(String instance, String name, String hornetAddress) throws ConfigurationException  {
        SimpleString queue=new SimpleString(hornetAddress+"-HornetQTmProvider");
        ppdb=PpDbFactory.getInstance(instance);
        
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
	public void setPpListener(PpListener ppListener) {
	    this.ppListener=ppListener;
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
            ParameterData pd = (ParameterData)Protocol.decode(msg, ParameterData.newBuilder());
            List<ParameterValue> params=new ArrayList<ParameterValue>();
            for( org.yamcs.protobuf.Pvalue.ParameterValue gpv : pd.getParameterList() ) {
                String processedParameterName = gpv.getId().getName();
                ProcessedParameterDefinition ppdef=ppdb.getProcessedParameter(processedParameterName);
                if(ppdef==null) continue;
                
                if( processedParameterName == null || "".equals( processedParameterName ) ) {
                    throw new InvalidParameterException( "Processed Parameter must have a name." );
                }
                ParameterValue pv = ParameterValue.fromGpb(ppdef, gpv);
                params.add(pv);
            }
            
            
            packetcount++;
            ppListener.updatePps(pd.getGenerationTime(), pd.getGroup(), pd.getSeqNum(), params);
            //System.out.println("mark 1: message received: "+msg);
            //ppListener.processPacket(pwt);
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

