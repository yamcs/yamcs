package org.yamcs.client;

import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.Commanding.CommandQueueEvent;
import org.yamcs.protobuf.SubscribeQueueEventsRequest;

public class QueueEventSubscription extends AbstractSubscription<SubscribeQueueEventsRequest, CommandQueueEvent> {

    protected QueueEventSubscription(WebSocketClient client) {
        super(client, "queue-events", CommandQueueEvent.class);
    }
}
