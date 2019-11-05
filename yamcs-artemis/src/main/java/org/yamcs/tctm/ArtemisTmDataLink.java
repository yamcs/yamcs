package org.yamcs.tctm;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.artemis.AbstractArtemisTranslatorService;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractService;

/**
 * receives data from Artemis ActiveMQ and publishes it into a yamcs stream
 * 
 * @author nm
 *
 */
public class ArtemisTmDataLink extends AbstractService implements TmPacketDataLink, MessageHandler {
    protected volatile long packetcount = 0;
    protected volatile boolean disabled = false;

    protected Log log;
    private TmSink tmSink;
    final TimeService timeService;
    final String artemisAddress;
    ClientSession artemisSession;
    ServerLocator locator;
    ClientSessionFactory factory;
    ClientConsumer client;
    
    boolean preserveIncomingReceptionTime = false;
    YConfiguration config;
    final String linkName;

    public ArtemisTmDataLink(String instance, String name, String artemisAddress) throws ConfigurationException {
        this.artemisAddress = artemisAddress;
        this.linkName = name;
        log = new Log(getClass(), instance);
        log.setContext(name);
        timeService = YamcsServer.getTimeService(instance);
        locator = AbstractArtemisTranslatorService.getServerLocator(instance);

    }

    public ArtemisTmDataLink(String instance, String name, YConfiguration config) throws ConfigurationException {
        this(instance, name, config.getString("address"));
        preserveIncomingReceptionTime = config.getBoolean("preserveIncomingReceptionTime", false);
    }

    @Override
    public void setTmSink(TmSink tmProcessor) {
        this.tmSink = tmProcessor;
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
        return packetcount;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        packetcount = 0;
    }

    @Override
    public void onMessage(ClientMessage msg) {
        try {
            msg.acknowledge();
            if (disabled) {
                return;
            }
            TmPacketData tm = (TmPacketData) Protocol.decode(msg, TmPacketData.newBuilder());
            packetcount++;
            long rectime = preserveIncomingReceptionTime ? TimeEncoding.fromProtobufTimestamp(tm.getReceptionTime())
                    : timeService.getMissionTime();
            TmPacket pwt = new TmPacket(rectime, TimeEncoding.fromProtobufTimestamp(tm.getGenerationTime()),
                    tm.getSequenceNumber(), tm.getPacket().toByteArray());
            tmSink.processPacket(pwt);
        } catch (Exception e) {
            log.warn("{} for message: {}", e.getMessage(), msg);
        }
    }

    @Override
    protected void doStart() {

        try {
            factory = locator.createSessionFactory();
            artemisSession = factory.createSession(false, true, true, true);
            String queue = artemisAddress + "-ActiveMQTmProvider";
            artemisSession.createTemporaryQueue(artemisAddress, queue);
            log.debug("Starting artemis tm data link connected to {}.{}", artemisAddress, queue);
            client = artemisSession.createConsumer(queue,
                    AbstractArtemisTranslatorService.UNIQUEID_HDR_NAME + "<>"
                            + AbstractArtemisTranslatorService.UNIQUEID);
            client.setMessageHandler(this);
            artemisSession.start();
            notifyStarted();
        } catch (Exception e) {
            log.error("Failed to set connect to artemis");
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        try {
            artemisSession.close();
            locator.close();
            notifyStopped();
        } catch (ActiveMQException e) {
            log.error("Got exception when quiting:", e);
            notifyFailed(e);
        }
    }

    @Override
    public String getName() {
        return linkName;
    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }

}
