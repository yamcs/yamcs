package org.yamcs.client;

import java.util.EventListener;

/**
 * A connection listener interface for clients connecting to Yamcs.
 */
public interface ConnectionListener extends EventListener {

    /**
     * Called right before the initial connection to Yamcs is being made.
     */
    public void connecting();

    /**
     * Called after a successful connection to Yamcs has been established.
     */
    public void connected();

    /**
     * Called when the initial connection to Yamcs has failed, e.g. the maximum number of retry attempts has exceeded.
     * 
     * @param cause
     *            Optional cause of the connection failure, may be null.
     */
    public void connectionFailed(Throwable cause);

    /**
     * Called when the connection to Yamcs is closed.
     */
    public void disconnected();

    /**
     * Used to log messages.
     * 
     * @param message
     *            the messages to be logged
     */
    default void log(String message) {
    }
}
