package org.yamcs.artemis;


import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Receives command history data from Artemis queues and publishes into yamcs streams
 * 
 * @author nm
 *
 */
public class ArtemisCmdHistoryDataLink extends AbstractService {
    String instance;
    ServerLocator locator;
    ClientSession session;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public ArtemisCmdHistoryDataLink(String instance) {
        this.instance = instance;
        locator = AbstractArtemisTranslatorService.getServerLocator(instance);
    }

    @Override
    protected void doStart() {
        try {
            session = locator.createSessionFactory().createSession();
            CmdHistoryTupleTranslator translator = new CmdHistoryTupleTranslator();
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
            StreamConfig sc = StreamConfig.getInstance(instance);
            for (String s : sc.getStreamNames(StandardStreamType.cmdHist)) {
                Stream stream = ydb.getStream(s);
                String address = instance + "." + s;
                String queue = address + "-StreamAdapter";
                log.debug("Subscribing to {}:{}", address, queue);
                session.createTemporaryQueue(address, queue);
                ClientConsumer client = session.createConsumer(queue);
                client.setMessageHandler(msg -> {
                    try {
                        Tuple tuple = translator.buildTuple(msg);
                        stream.emitTuple(tuple);
                    } catch (IllegalArgumentException e) {
                        log.warn("Cannot decode cmdhist message: {} from artemis message: {}", e.getMessage(), msg);
                    } 
                });
            }
            session.start();
            notifyStarted();
        } catch (Exception e) {
            log.error("Error creating the subcription to artemis", e);
            notifyFailed(e);
        }
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
