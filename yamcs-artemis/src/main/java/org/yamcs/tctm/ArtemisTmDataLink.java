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
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.artemis.AbstractArtemisTranslatorService;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.time.TimeService;

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

    protected Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private TmSink tmSink;
    final TimeService timeService;
    final String artemisAddress;
    ClientSession artemisSession;
    ServerLocator locator;

    boolean preserveIncomingReceptionTime = false;

    public ArtemisTmDataLink(String instance, String name, String artemisAddress) throws ConfigurationException {
        this.artemisAddress = artemisAddress;
        timeService = YamcsServer.getTimeService(instance);
        locator = AbstractArtemisTranslatorService.getServerLocator(instance);
    }

    public ArtemisTmDataLink(String instance, String name, Map<String, Object> args) throws ConfigurationException {
        this(instance, name, YConfiguration.getString(args, "address"));

        if (YConfiguration.getBoolean(args, "preserveIncomingReceptionTime", false)) {
            preserveIncomingReceptionTime = true;
        }
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
    public void onMessage(ClientMessage msg) {
        try {
            msg.acknowledge();
            if (disabled) {
                return;
            }
            TmPacketData tm = (TmPacketData) Protocol.decode(msg, TmPacketData.newBuilder());
            packetcount++;
            long rectime = preserveIncomingReceptionTime ? tm.getReceptionTime() : timeService.getMissionTime();
            PacketWithTime pwt = new PacketWithTime(rectime,
                    tm.getGenerationTime(), tm.getSequenceNumber(), tm.getPacket().toByteArray());
            tmSink.processPacket(pwt);
        } catch (Exception e) {
            log.warn("{} for message: {}", e.getMessage(), msg);
        }
    }

    @Override
    protected void doStart() {

        try {
            artemisSession = locator.createSessionFactory().createSession(false, true, true, true);
            String queue = artemisAddress + "-ActiveMQTmProvider";
            artemisSession.createTemporaryQueue(artemisAddress, queue);
            log.debug("Starting artemis tm data link connected to {}.{}", artemisAddress, queue);
            ClientConsumer client = artemisSession.createConsumer(queue,
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
            notifyStopped();
        } catch (ActiveMQException e) {
            log.error("Got exception when quiting:", e);
            notifyFailed(e);
        }
    }

}
