package org.yamcs.client;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.SubscribeTimeRequest;

import com.google.protobuf.Timestamp;

/**
 * Subscription for receiving time updates.
 */
public class TimeSubscription extends AbstractSubscription<SubscribeTimeRequest, Timestamp> {

    private volatile Instant latest;
    private Set<TimeListener> timeListeners = new CopyOnWriteArraySet<>();

    protected TimeSubscription(MethodHandler methodHandler) {
        super(methodHandler, "time", Timestamp.class);
        addMessageListener(new MessageListener<Timestamp>() {
            @Override
            public void onMessage(Timestamp timestamp) {
                latest = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
                timeListeners.forEach(l -> l.onUpdate(latest));
            }

            @Override
            public void onError(Throwable t) {
                timeListeners.forEach(l -> l.onError(t));
            }
        });
    }

    public void addListener(TimeListener listener) {
        timeListeners.add(listener);
    }

    public void removeListener(TimeListener listener) {
        timeListeners.remove(listener);
    }

    /**
     * Returns the value of the latest received timestamp.
     */
    public Instant getCurrent() {
        return latest;
    }
}
