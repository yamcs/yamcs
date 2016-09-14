package org.yamcs;

import org.yamcs.security.AuthenticationToken;

/**
 * A client of a processor
 * @author nm
 *
 */
public interface ProcessorClient {
    /**
     * change the connection to another processor
     * @param p - processor 
     */
    public void switchProcessor(YProcessor p, AuthenticationToken authToken) throws ProcessorException;
    /**
     * called when the processor is closing down
     */
    void yProcessorQuit();

    public String getUsername();
    public String getApplicationName();
}
