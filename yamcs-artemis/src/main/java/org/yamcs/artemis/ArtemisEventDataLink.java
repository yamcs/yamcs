package org.yamcs.artemis;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.StreamConfig;
import org.yamcs.YConfiguration;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Receives event data from Artemis queues and publishes into yamcs streams
 * 
 * @author nm
 *
 */
public class ArtemisEventDataLink extends AbstractYamcsService {

    private ServerLocator locator;
    private ClientSession artemisSession;
    private ClientConsumer client;
    private ClientSessionFactory factory;

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);
        locator = AbstractArtemisTranslatorService.getServerLocator(yamcsInstance);
    }

    @Override
    protected void doStart() {
        try {
            factory = locator.createSessionFactory();
            artemisSession = factory.createSession();
            EventTupleTranslator translator = new EventTupleTranslator();
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
            for (String streamName : sc.getStreamNames(StreamConfig.StandardStreamType.event)) {
                Stream stream = ydb.getStream(streamName);
                String address = yamcsInstance + "." + streamName;
                String queue = address + "-StreamAdapter";
                log.debug("Subscribing to {}:{}", address, queue);
                artemisSession.createTemporaryQueue(address, queue);
                client = artemisSession.createConsumer(queue);
                client.setMessageHandler((msg) -> {
                    try {
                        msg.acknowledge();
                        Tuple tuple = translator.buildTuple(msg);
                        stream.emitTuple(tuple);
                    } catch (Exception e) {
                        log.warn("{} for message: {}", e.getMessage(), msg);
                    }
                });
            }
            artemisSession.start();
            notifyStarted();
        } catch (Exception e) {
            log.error("Error creating the subcription to artemis", e);
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
            notifyFailed(e);
        }
    }
}
