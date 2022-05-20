package org.yamcs.tests;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.yamcs.client.Subscription;

import com.google.protobuf.Message;

/**
 * Utility class for queueing original messages as they are received from a subscription to Yamcs. This is intended for
 * use in unit tests only.
 */
public class MessageCaptor<T extends Message> {

    // The maximum wait time before a timeout exception is generated on 'timely' methods.
    // On slow machines this can be increased, but it may also have an impact on code
    // that _expects_ to receive nothing for a certain time.
    private static final int TIMELY_WAIT_TIME = 3000;

    private BlockingQueue<T> queue = new LinkedBlockingQueue<>();

    private MessageCaptor(Subscription<?, T> subscription) {
        subscription.addMessageListener(queue::add);
    }

    /**
     * Returns the next received message. Or null, if nothing was received yet.
     */
    public T poll() {
        return queue.poll();
    }

    public T poll(long timeout) throws InterruptedException {
        return queue.poll(timeout, TimeUnit.MILLISECONDS);
    }

    public T expectTimely() throws InterruptedException, TimeoutException {
        T message = queue.poll(TIMELY_WAIT_TIME, TimeUnit.MILLISECONDS);
        if (message == null) {
            throw new TimeoutException();
        }
        return message;
    }

    public void clear() {
        queue.clear();
    }

    /**
     * Returns true if there are not currently any unprocessed messages.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Returns the number of unprocessed messages.
     */
    public int getPendingCount() {
        return queue.size();
    }

    public static <T extends Message> MessageCaptor<T> of(Subscription<?, T> subscription) {
        return new MessageCaptor<>(subscription);
    }
}
