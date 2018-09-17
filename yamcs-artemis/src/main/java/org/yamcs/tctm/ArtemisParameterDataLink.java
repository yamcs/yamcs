package org.yamcs.tctm;

import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.api.artemis.Protocol;
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
public class ArtemisParameterDataLink extends AbstractService implements ParameterDataLink, MessageHandler {
    protected volatile long totalPpCount = 0;
    protected volatile boolean disabled = false;

    protected Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private ParameterSink ppListener;
    final XtceDb ppdb;
    final String artemisAddress;
    ClientSession artemisSession;
    ServerLocator locator;

    public ArtemisParameterDataLink(String instance, String name, String artemisAddress) throws ConfigurationException {
        ppdb = XtceDbFactory.getInstance(instance);
        this.artemisAddress = artemisAddress;
        locator = AbstractArtemisTranslatorService.getServerLocator(instance);
    }

    public ArtemisParameterDataLink(String instance, String name, Map<String, Object> args)
            throws ConfigurationException {
        this(instance, name, YConfiguration.getString(args, "address"));
    }

    @Override
    public void setParameterSink(ParameterSink ppListener) {
        this.ppListener = ppListener;
    }

    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        } else {
            return Status.OK;
        }
    }

    @Override
    public void disable() {
        disabled = true;
    }

    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public String getDetailedStatus() {
        if (disabled) {
            return "DISABLED";
        } else {
            return "OK";
        }
    }

    @Override
    public long getDataInCount() {
        return totalPpCount;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void onMessage(ClientMessage msg) {
        try {
            msg.acknowledge();
            if (disabled) {
                return;
            }
            ParameterData pd = (ParameterData) Protocol.decode(msg, ParameterData.newBuilder());
            long genTime;
            if (pd.hasGenerationTime()) {
                genTime = pd.getGenerationTime();
            } else {
                Long l = msg.getLongProperty(StandardTupleDefinitions.PARAMETER_COL_GENTIME);
                if (l != null) {
                    genTime = l;
                } else {
                    log.warn("Cannot find generation time either in the body or in the header of the message");
                    return;
                }
            }
            String ppGroup;
            if (pd.hasGroup()) {
                ppGroup = pd.getGroup();
            } else {
                ppGroup = msg.getStringProperty(StandardTupleDefinitions.PARAMETER_COL_GROUP);
                if (ppGroup == null) {
                    log.warn("Cannot find PP group either in the body or in the header of the message");
                    return;
                }
            }
            totalPpCount += pd.getParameterCount();
            ppListener.updateParams(genTime, ppGroup, pd.getSeqNum(), pd.getParameterList());
        } catch (Exception e) {
            log.warn("{} for message: {}", e.getMessage(), msg);
        }
    }

    @Override
    protected void doStart() {
        try {
            artemisSession = locator.createSessionFactory().createSession();
            String queue = artemisAddress + "-ArtemisPpProvider";
            log.debug("Starting artemis parameter data link connected to {}.{}", artemisAddress, queue);
            artemisSession.createTemporaryQueue(artemisAddress, queue);
            ClientConsumer client = artemisSession.createConsumer(queue,
                    AbstractArtemisTranslatorService.UNIQUEID_HDR_NAME + "<>"
                            + AbstractArtemisTranslatorService.UNIQUEID);
            client.setMessageHandler(this);
            artemisSession.start();
            notifyStarted();
        } catch (Exception e) {
            log.error("Failed connect to artemis");
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        try {
            artemisSession.close();
            notifyStopped();
        } catch (ActiveMQException e) {
            log.error("Got exception when quiting:", e);
            notifyFailed(e);
        }
    }
}
