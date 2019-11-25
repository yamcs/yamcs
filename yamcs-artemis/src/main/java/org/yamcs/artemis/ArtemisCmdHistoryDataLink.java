package org.yamcs.artemis;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.YConfiguration;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Receives command history data from Artemis queues and publishes into yamcs streams
 * 
 * @author nm
 *
 */
public class ArtemisCmdHistoryDataLink extends AbstractYamcsService {

    private ServerLocator locator;
    private ClientSession session;
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
            session = factory.createSession();
            CmdHistoryTupleTranslator translator = new CmdHistoryTupleTranslator();
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
            for (String streamName : sc.getStreamNames(StandardStreamType.cmdHist)) {
                Stream stream = ydb.getStream(streamName);
                String address = yamcsInstance + "." + streamName;
                String queue = address + "-StreamAdapter";
                log.debug("Subscribing to {}:{}", address, queue);
                session.createTemporaryQueue(address, queue);
                client = session.createConsumer(queue);
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
            locator.close();
            notifyStopped();
        } catch (ActiveMQException e) {
            notifyFailed(e);
        }
    }
}
