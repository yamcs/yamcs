package org.yamcs.parameter;

import org.yamcs.mdb.ProcessingContext;

/**
 * This is the interface implemented by the ParameterRequestManager to receive parameters from 
 * the different parameter providers.  
 *
 */
public interface ParameterProcessor {
    public void process(ProcessingContext processingCtx);
}
