package org.yamcs.parameter;

public interface ParameterRetrievalConsumer<T> {
    /**
     * Called with new data
     */
    void accept(T t);

    /**
     * Called when the retrieval is finished
     */
    default void finished() {
    }

    /**
     * Called when there was a failure.
     * <p>
     * If this is received, the retrieval is finished; no new data will be sent and the finished will not be called
     * either
     * 
     */
    default void failed(Throwable t) {

    }
}
