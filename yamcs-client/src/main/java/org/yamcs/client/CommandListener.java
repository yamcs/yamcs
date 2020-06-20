package org.yamcs.client;

@FunctionalInterface
public interface CommandListener {

    void onUpdate(Command command);

    /**
     * Called when an exception claused the call to abort.
     */
    default void onError(Throwable t) {
    }
}
