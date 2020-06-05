package org.yamcs.events;

import org.yamcs.yarch.protobuf.Db.Event;

/**
 * Event producer that swallows the events
 * @author nm
 *
 */
public class QuietEventProducer extends AbstractEventProducer {

    @Override
    public void sendEvent(Event event) {
        //do nothing
    }

    @Override
    public void close() {
        //do nothing
    }

    @Override
    public long getMissionTime() {
        return 0;
    }

}
