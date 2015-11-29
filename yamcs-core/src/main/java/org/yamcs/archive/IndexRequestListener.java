package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.IndexResult;

/**
 * Used by {@link IndexRequestProcessor}
 */
public interface IndexRequestListener {
        
    /**
     * Process the index chunk. If indexResult is null, the end was reached
     */
    void processData(IndexResult indexResult) throws Exception;
    
    /**
     * Called right after the processing ended, either successfully or through
     * error
     */
    void finished(boolean success);
}
