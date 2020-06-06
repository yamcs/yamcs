package org.yamcs.client;

import java.time.Instant;

import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.protobuf.SubscribeTimeRequest;

import com.google.protobuf.Timestamp;

/**
 * Subscription for receiving time updates.
 */
public class TimeSubscription extends AbstractSubscription<SubscribeTimeRequest, Timestamp> {

    private volatile Instant latest;

    protected TimeSubscription(WebSocketClient client) {
        super(client, "time", Timestamp.class);
        addMessageListener(this::processMessage);
    }

    protected void processMessage(Timestamp timestamp) {
        latest = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    /**
     * Returns the value of the latest received timestamp.
     */
    public Instant getCurrent() {
        return latest;
    }
}
