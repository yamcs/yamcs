package org.yamcs.api;

public interface Observer<T> {

    /**
     * Emit the next message.
     */
    void next(T message);

    /**
     * Complete with an exception.
     */
    void completeExceptionally(Throwable t);

    /**
     * Mark the successful end.
     */
    void complete();

    /**
     * Shortcut for:
     * 
     * <pre>
     * next(message);
     * complete();
     * </pre>
     */
    default void complete(T message) {
        next(message);
        complete();
    }

    /**
     * Returns whether this call has been cancelled by the remote peer
     */
    boolean isCancelled();

    /**
     * Set a {@link Runnable} that will be called when the call is cancelled. (example: peer disconnect)
     */
    void setCancelHandler(Runnable cancelHandler);
}
