package org.yamcs.client;

import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.protobuf.Yamcs.Event;

/**
 * Subscription for receiving events.
 */
public class EventSubscription extends AbstractSubscription<SubscribeEventsRequest, Event> {

    protected EventSubscription(WebSocketClient client) {
        super(client, "events", Event.class);
    }
}
