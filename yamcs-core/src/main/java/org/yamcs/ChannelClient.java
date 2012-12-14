package org.yamcs;

/**
 * A client of a channel - receives processed TM, sends TC
 * @author nm
 *
 */
public interface ChannelClient {
    /**
     * change the connection to another channel
     * @param newChannel
     */
    
    public void switchChannel(Channel c) throws ChannelException;
    /**
     * called when the channel is closing down
     */
    void channelQuit();

    public String getUsername();
    public String getApplicationName();
}
