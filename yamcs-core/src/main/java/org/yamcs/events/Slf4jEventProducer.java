package org.yamcs.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.utils.TimeEncoding;

/**
 * Prints all events via java logging.
 */
public class Slf4jEventProducer extends AbstractEventProducer {

    private static final Logger log = LoggerFactory.getLogger(Slf4jEventProducer.class);

    public Slf4jEventProducer() {
        this.logAllMessages = false;
    }

    @Override
    public void sendEvent(Event event) {
        StringBuilder buf = new StringBuilder();
        if (event.hasSource()) {
            buf.append("[").append(event.getSource()).append("]");
        }
        if (event.hasType()) {
            buf.append("[").append(event.getType()).append("]");
        }
        if (event.hasSource() || event.hasType()) {
            buf.append(" ");
        }
        buf.append(event.getMessage());
        if (event.hasSeverity()) {
            switch (event.getSeverity()) {
            case WATCH:
            case WARNING:
                log.warn(buf.toString());
                break;
            case DISTRESS:
            case CRITICAL:
            case SEVERE:
                log.error(buf.toString());
                break;
            default:
                log.info(buf.toString());
            }
        }
    }

    @Override
    public void close() {
    }

    @Override
    public long getMissionTime() {
        return TimeEncoding.getWallclockTime();
    }
}
