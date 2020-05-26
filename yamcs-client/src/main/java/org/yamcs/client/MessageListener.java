package org.yamcs.client;

import com.google.protobuf.Message;

/**
 * A listener for handling data messages received from a WebSocket call.
 */
@FunctionalInterface
public interface MessageListener<T extends Message> {

    /**
     * Called when a single data message is received. Implementations should return quickly.
     */
    void onMessage(T message);

    /**
     * Called when an exception claused the call to abort.
     */
    default void onError(Throwable t) {
    }
}
