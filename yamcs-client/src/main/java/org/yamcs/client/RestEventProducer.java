package org.yamcs.client;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.api.AbstractEventProducer;
import org.yamcs.protobuf.Archive.CreateEventRequest;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.Yaml;

import io.netty.handler.codec.http.HttpMethod;

/**
 * An EventProducer that publishes events over the web api
 * <p>
 * By default, repeated message are detected and reduced, resulting in pseudo events with a message like 'last event
 * repeated X times'. This behaviour can be turned off.
 */
public class RestEventProducer extends AbstractEventProducer {
    static final String CONF_REPEATED_EVENT_REDUCTION = "repeatedEventReduction";

    static Logger logger = LoggerFactory.getLogger(RestEventProducer.class);

    static final int MAX_QUEUE_SIZE = 1000;
    ArrayBlockingQueue<Event> queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);

    String eventResource;
    private YamcsClient client;

    public RestEventProducer(YamcsClient client) {
        this.client = client;

        String effectiveInstance = client.getConnectionInfo().getInstance().getName();
        eventResource = "/archive/" + effectiveInstance + "/events";
        InputStream is = RestEventProducer.class.getResourceAsStream("/event-producer.yaml");
        boolean repeatedEventReduction = true;
        if (is != null) {
            Object o = new Yaml().load(is);
            if (!(o instanceof Map<?, ?>)) {
                throw new ConfigurationException("event-producer.yaml does not contain a map but a " + o.getClass());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;

            if (m.containsKey(CONF_REPEATED_EVENT_REDUCTION)) {
                repeatedEventReduction = (Boolean) m.get(CONF_REPEATED_EVENT_REDUCTION);
            }
        }
        if (repeatedEventReduction) {
            setRepeatedEventReduction(true, 60000);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public synchronized void sendEvent(Event event) {
        logger.debug("Sending Event: {}", event.getMessage());
        CreateEventRequest.Builder req = CreateEventRequest.newBuilder();
        req.setMessage(event.getMessage());
        if (event.hasGenerationTimeUTC()) {
            req.setTime(event.getGenerationTimeUTC());
        } else if (event.hasGenerationTime()) {
            req.setTime(TimeEncoding.toString(event.getGenerationTime()));
        }
        if (event.hasSeverity()) {
            req.setSeverity(event.getSeverity().toString());
        }
        if (event.hasType()) {
            req.setType(event.getType());
        }
        if (event.hasSource()) {
            req.setSource(event.getSource());
            if (event.hasSeqNumber()) { // 'should' be linked to source
                req.setSequenceNumber(event.getSeqNumber());
            }
        }
        client.getRestClient().doRequest(eventResource, HttpMethod.POST, req.build().toByteArray());
    }

    @Override
    public String toString() {
        return RestEventProducer.class.getName() + " sending events to " + client.getUrl();
    }

    @Override
    public long getMissionTime() {
        return TimeEncoding.getWallclockTime();
    }
}
