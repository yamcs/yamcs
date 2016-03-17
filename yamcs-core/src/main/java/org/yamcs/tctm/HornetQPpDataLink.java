package org.yamcs.tctm;


import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.hornetq.StreamAdapter;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;


/**
 * receives data from HornetQ and publishes it into a yamcs stream
 * 
 * @author nm
 *
 */
public class HornetQPpDataLink extends  AbstractService implements PpDataLink, MessageHandler {
    protected volatile long totalPpCount = 0;
    protected volatile boolean disabled=false;

    protected Logger log=LoggerFactory.getLogger(this.getClass().getName());
    private PpListener ppListener;
    YamcsSession yamcsSession; 
    final private YamcsClient msgClient;
    final XtceDb ppdb;

    public HornetQPpDataLink(String instance, String name, String hornetAddress) throws ConfigurationException  {
        SimpleString queue=new SimpleString(hornetAddress+"-HornetQPpProvider");
        ppdb=XtceDbFactory.getInstance(instance);

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
        return totalPpCount;
    }


    @Override
    public void onMessage(ClientMessage msg) {
        if(disabled) return;
        try {
            ParameterData pd = (ParameterData)Protocol.decode(msg, ParameterData.newBuilder());
            long genTime;
            if(pd.hasGenerationTime()) {
                genTime = pd.getGenerationTime();
            } else {
                Long l = msg.getLongProperty(PpProviderAdapter.PP_TUPLE_COL_GENTIME);
                if(l!=null) {
                    genTime = l;
                } else {
                    log.warn("Cannot find generation time either in the body or in the header of the message");
                    return;
                }
            }
            String ppGroup;
            if(pd.hasGroup()) {
                ppGroup = pd.getGroup();
            } else {
                ppGroup = msg.getStringProperty(PpProviderAdapter.PP_TUPLE_COL_PPGROUP);
                if(ppGroup == null) {
                    log.warn("Cannot find PP group either in the body or in the header of the message");
                    return;
                }
            }
            totalPpCount += pd.getParameterCount();
            ppListener.updateParams(genTime, ppGroup, pd.getSeqNum(), pd.getParameterList());
        } catch(Exception e){
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

