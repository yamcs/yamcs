package org.yamcs;

import org.yamcs.security.AuthenticationToken;

/**
 * A client of a processor - receives processed TM, sends TC
 * @author nm
 *
 */
public interface YProcessorClient {
    /**
     * change the connection to another processor
     * @param p - processor 
     */
    
    public void switchYProcessor(YProcessor p, AuthenticationToken authToken) throws YProcessorException;
    /**
     * called when the channel is closing down
     */
    void yProcessorQuit();

    public String getUsername();
    public String getApplicationName();
}
