package org.yamcs.client;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.links.LinkEvent;
import org.yamcs.protobuf.links.SubscribeLinksRequest;

/**
 * Subscription for receiving link-related events.
 */
public class LinkSubscription extends AbstractSubscription<SubscribeLinksRequest, LinkEvent> {

    protected LinkSubscription(MethodHandler methodHandler) {
        super(methodHandler, "links", LinkEvent.class);
    }
}
