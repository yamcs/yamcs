package org.yamcs.client;

import java.time.Instant;

@FunctionalInterface
public interface CommandListener {

    void onUpdate(Instant time);

    /**
     * Called when an exception claused the call to abort.
     */
    default void onError(Throwable t) {
    }
}
