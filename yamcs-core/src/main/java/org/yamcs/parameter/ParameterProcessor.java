package org.yamcs.parameter;

import org.yamcs.mdb.ProcessingData;

/**
 * This is the interface implemented by the ParameterRequestManager to receive parameters from 
 * the different parameter providers.  
 * 
 * @author nm
 *
 */
public interface ParameterProcessor {

    public void process(ProcessingData processingData);
}
