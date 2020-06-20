package org.yamcs.client;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.Commanding.CommandQueueEvent;
import org.yamcs.protobuf.SubscribeQueueEventsRequest;

public class QueueEventSubscription extends AbstractSubscription<SubscribeQueueEventsRequest, CommandQueueEvent> {

    protected QueueEventSubscription(MethodHandler methodHandler) {
        super(methodHandler, "queue-events", CommandQueueEvent.class);
    }
}
