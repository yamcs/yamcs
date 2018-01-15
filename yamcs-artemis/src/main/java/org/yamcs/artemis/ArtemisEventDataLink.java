package org.yamcs.artemis;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StreamConfig;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Receives event data from Artemis queues and publishes into yamcs streams
 * 
 * @author nm
 *
 */
public class ArtemisEventDataLink extends AbstractService {
    String instance;
    ServerLocator locator;
    ClientSession session;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public ArtemisEventDataLink(String instance) {
        this.instance = instance;
        locator = AbstractArtemisTranslatorService.getServerLocator(instance);
    }

    @Override
    protected void doStart() {
        try {
            session = locator.createSessionFactory().createSession();
            EventTupleTranslator translator = new EventTupleTranslator();
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
            StreamConfig sc = StreamConfig.getInstance(instance);
            for (String streamName : sc.getStreamNames(StreamConfig.StandardStreamType.event)) {
                Stream stream = ydb.getStream(streamName);
                String address = instance + "." + streamName;
                String queue = address + "-StreamAdapter";
                session.createTemporaryQueue(address, queue);
                ClientConsumer client = session.createConsumer(queue);
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
            session.start();
        } catch (Exception e) {
            notifyFailed(e);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        try {
            session.stop();
            session.close();
            notifyStopped();
        } catch (ActiveMQException e) {
            notifyFailed(e);
        }
    }

}
