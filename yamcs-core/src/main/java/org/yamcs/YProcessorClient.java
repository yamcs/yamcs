package org.yamcs;

import org.yamcs.security.AuthenticationToken;

/**
 * A client of a channel - receives processed TM, sends TC
 * @author nm
 *
 */
public interface YProcessorClient {
    /**
     * change the connection to another channel
     * @param newChannel
     */
    
    public void switchYProcessor(YProcessor c, AuthenticationToken authToken) throws YProcessorException;
    /**
     * called when the channel is closing down
     */
    void yProcessorQuit();

    public String getUsername();
    public String getApplicationName();
}
