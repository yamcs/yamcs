package org.yamcs.tctm;


import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.artemis.AbstractArtemisTranslatorService;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * receives data from ActiveMQ Artemis and publishes it into a yamcs stream
 * 
 * @author nm
 *
 */
public class ArtemisParameterDataLink extends  AbstractService implements ParameterDataLink, MessageHandler {
    protected volatile long totalPpCount = 0;
    protected volatile boolean disabled=false;

    protected Logger log=LoggerFactory.getLogger(this.getClass().getName());
    private ParameterSink ppListener;
    private YamcsSession yamcsSession; 
    final XtceDb ppdb;
    final String hornetAddress; 
    
    public ArtemisParameterDataLink(String instance, String name, String hornetAddress) throws ConfigurationException  {
        ppdb = XtceDbFactory.getInstance(instance);
        this.hornetAddress = hornetAddress;
    }


    @Override
    public void setParameterSink(ParameterSink ppListener) {
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
        if(disabled) {
            return;
        }
        try {
            ParameterData pd = (ParameterData)Protocol.decode(msg, ParameterData.newBuilder());
            long genTime;
            if(pd.hasGenerationTime()) {
                genTime = pd.getGenerationTime();
            } else {
                Long l = msg.getLongProperty(ParameterDataLinkInitialiser.PARAMETER_TUPLE_COL_GENTIME);
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
                ppGroup = msg.getStringProperty(ParameterDataLinkInitialiser.PARAMETER_TUPLE_COL_GROUP);
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
            SimpleString queue=new SimpleString(hornetAddress+"-ActiveMQPpProvider");
            yamcsSession = YamcsSession.newBuilder().build();
            YamcsClient yclient = yamcsSession.newClientBuilder().setDataProducer(false).setDataConsumer(new SimpleString(hornetAddress), queue).
                    setFilter(new SimpleString(AbstractArtemisTranslatorService.UNIQUEID_HDR_NAME+"<>"+AbstractArtemisTranslatorService.UNIQUEID)).
                    build();

            yclient.dataConsumer.setMessageHandler(this);
            notifyStarted();
        } catch (ActiveMQException|YamcsApiException e) {
            log.error("Failed connect to artemis");
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        try {
            yamcsSession.close();
            notifyStopped();
        } catch (ActiveMQException e) {
            log.error("Got exception when quiting:", e);
            notifyFailed(e);
        }
    }
}

