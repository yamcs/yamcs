package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.yamcs.client.ParameterSubscription;
import org.yamcs.client.ParameterSubscription.Listener;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

/**
 * Utility class for queueing parameter deliveries as they are received from a subscription to Yamcs. This is intended
 * for use in unit tests only.
 */
public class ParameterCaptor {

    // The maximum wait time before a timeout exception is generated on 'timely' methods.
    // On slow machines this can be increased, but it may also have an impact on code
    // that _expects_ to receive nothing for a certain time.
    private static final int TIMELY_WAIT_TIME = 3000;

    private BlockingQueue<NamedObjectId> invalidIdentifierQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<List<ParameterValue>> deliveryQueue = new LinkedBlockingQueue<>();

    private ParameterCaptor(ParameterSubscription subscription) {
        subscription.addListener(new Listener() {

            @Override
            public void onData(List<ParameterValue> values) {
                deliveryQueue.add(values);
            }

            @Override
            public void onInvalidIdentification(NamedObjectId id) {
                invalidIdentifierQueue.add(id);
            }
        });
    }

    /**
     * Returns the next delivery. Or null, if nothing was received yet.
     */
    public List<ParameterValue> poll() {
        return deliveryQueue.poll();
    }

    /**
     * Returns the next delivery. Or null, if nothing was received yet.
     */
    public List<ParameterValue> poll(long timeout) throws InterruptedException {
        return deliveryQueue.poll(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the next delivery. If there is nothing available yet, this will block for the specified time awaiting
     * delivery.
     */
    public List<ParameterValue> expectTimely() throws InterruptedException, TimeoutException {
        List<ParameterValue> values = deliveryQueue.poll(TIMELY_WAIT_TIME, TimeUnit.MILLISECONDS);
        if (values == null) {
            throw new TimeoutException();
        }
        return values;
    }

    public void assertSilence() throws InterruptedException {
        List<ParameterValue> values = deliveryQueue.poll(TIMELY_WAIT_TIME, TimeUnit.MILLISECONDS);
        assertNull(values, "Received a message while a moment of silence was expected");
    }

    public void clear() {
        deliveryQueue.clear();
        invalidIdentifierQueue.clear();
    }

    /**
     * Returns true if there are not currently any unprocessed deliveries.
     */
    public boolean isEmpty() {
        return deliveryQueue.isEmpty();
    }

    /**
     * Returns the number of unprocessed value deliveries.
     */
    public int getPendingCount() {
        return deliveryQueue.size();
    }

    public NamedObjectId expectTimelyInvalidIdentifier() throws InterruptedException, TimeoutException {
        NamedObjectId id = invalidIdentifierQueue.poll(TIMELY_WAIT_TIME, TimeUnit.MILLISECONDS);
        if (id == null) {
            throw new TimeoutException();
        }
        return id;
    }

    public static ParameterCaptor of(ParameterSubscription subscription) {
        return new ParameterCaptor(subscription);
    }
}
