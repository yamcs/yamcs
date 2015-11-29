package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.IndexResult;

/**
 * Used by {@link IndexRequestProcessor}
 */
public interface IndexRequestListener {
    
    /**
     * Called just before the processing is about to start. Any caught
     * exception, will halt further processing.
     */
    void beforeProcessing() throws Exception;
    
    /**
     * Process the index chunk. If indexResult is null, the end was reached
     */
    void processData(IndexResult indexResult) throws Exception;
    
    /**
     * Called right after the processing ended, either successfully or through
     * error
     */
    void afterProcessing(boolean success);
}
