package org.yamcs.client;

import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.SubscribeTimeRequest;

import com.google.protobuf.Timestamp;

/**
 * Subscription for receiving time updates.
 */
public class TimeSubscription extends AbstractSubscription<SubscribeTimeRequest, Timestamp> {

    private volatile Timestamp latest;

    protected TimeSubscription(WebSocketClient client) {
        super(client, "time", Timestamp.class);
        addMessageListener(this::processMessage);
    }

    protected void processMessage(Timestamp timestamp) {
        latest = timestamp;
    }

    /**
     * Returns the value of the latest received timestamp.
     */
    public Timestamp getCurrent() {
        return latest;
    }
}
