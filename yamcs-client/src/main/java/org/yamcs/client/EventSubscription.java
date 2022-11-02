package org.yamcs.client;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.SubscribeEventsRequest;

/**
 * Subscription for receiving events.
 */
public class EventSubscription extends AbstractSubscription<SubscribeEventsRequest, Event> {

    protected EventSubscription(MethodHandler methodHandler) {
        super(methodHandler, "events", Event.class);
    }
}
