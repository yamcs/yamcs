package org.yamcs.artemis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsService;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * takes data from yamcs streams and publishes it to artemis address
 * 
 *
 */
public class AbstractArtemisTranslatorService extends AbstractService implements YamcsService {
    final private TupleTranslator translator;
    YamcsSession yamcsSession;

    List<Stream> streams = new ArrayList<>();
    Map<Stream, StreamSubscriber> streamSubscribers = new HashMap<>();
    public static final String UNIQUEID_HDR_NAME = "_y_uniqueid";
    public static final int UNIQUEID = new Random().nextInt();
    public static final String ARTEMIS_URL_KEY = "artemisUrl";

    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    String instance;
    ServerLocator locator;

    private final ThreadLocal<ArtemisClient> artemisClient = new ThreadLocal<ArtemisClient>() {
        ArtemisClient client;

        @Override
        protected ArtemisClient initialValue() {
            try {
                client = new ArtemisClient();
                ClientSessionFactory factory = locator.createSessionFactory();
                client.session = factory.createSession();
                client.producer = client.session.createProducer();
                return client;
            } catch (Exception e) {
                throw new ConfigurationException("Cannot create a artemis client", e);
            }
        }
    };

    public AbstractArtemisTranslatorService(String instance, List<String> streamNames, TupleTranslator translator)
            throws ConfigurationException {
        this.locator = getServerLocator(instance);
        this.instance = instance;
        YarchDatabaseInstance db = YarchDatabase.getInstance(instance);

        for (String sn : streamNames) {
            Stream s = db.getStream(sn);
            if (s == null) {
                throw new ConfigurationException("Cannot find stream '" + sn + "'");
            }
            streams.add(s);
        }
        this.translator = translator;

        try {
        } catch (Exception e) {
            throw new ConfigurationException("Cannot create hornetq client", e);
        }
    }

    @Override
    public String toString() {
        return "ArtemisTmService";
    }

    @Override
    protected void doStart() {
        for (Stream s : streams) {
            final SimpleString artemisAddress = new SimpleString(instance + "." + s.getName());
            log.debug("Starting providing tuples from stream {} as messages on {} ActiveMQ address", s.getName(),
                    artemisAddress.toString());
            StreamSubscriber ss = new StreamSubscriber() {
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    try {
                        ArtemisClient ac = artemisClient.get();

                        ClientMessage msg = translator.buildMessage(ac.session.createMessage(false), tuple);
                        msg.putIntProperty(UNIQUEID_HDR_NAME, UNIQUEID);
                        ac.producer.send(artemisAddress, msg);
                    } catch (IllegalArgumentException | ActiveMQException e) {
                        log.warn("Got exception when sending message:", e);
                    }
                }

                @Override
                public void streamClosed(Stream stream) {
                    log.info("Stream " + stream + " closed");
                }

            };
            s.addSubscriber(ss);
            streamSubscribers.put(s, ss);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (Stream s : streams) {
            s.removeSubscriber(streamSubscribers.get(s));
        }
        notifyStopped();
    }

    static class ArtemisClient {
        ClientSession session;
        ClientProducer producer;
    }

    public static ServerLocator getServerLocator(String instance) {
        String artemisUrl = "vm:///"; // for compatibility with old yamcs

        YConfiguration yc = YConfiguration.getConfiguration("yamcs." + instance);
        if (yc.containsKey(ARTEMIS_URL_KEY)) {
            artemisUrl = yc.getString(ARTEMIS_URL_KEY);
        } else {
            yc = YConfiguration.getConfiguration("yamcs");
            if (yc.containsKey(ARTEMIS_URL_KEY)) {
                artemisUrl = yc.getString(ARTEMIS_URL_KEY);
            }
        }

        try {
            return ActiveMQClient.createServerLocator(artemisUrl);
        } catch (Exception e) {
            throw new ConfigurationException("Cannot create Artemis connection", e);
        }
    }
}
