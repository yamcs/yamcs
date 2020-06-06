package org.yamcs.client;

import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.LinkEvent;
import org.yamcs.protobuf.SubscribeLinksRequest;

/**
 * Subscription for receiving link-related events.
 */
public class LinkSubscription extends AbstractSubscription<SubscribeLinksRequest, LinkEvent> {

    protected LinkSubscription(WebSocketClient client) {
        super(client, "links", LinkEvent.class);
    }
}
