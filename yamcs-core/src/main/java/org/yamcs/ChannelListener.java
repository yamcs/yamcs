package org.yamcs;

public interface ChannelListener {

    void channelAdded(Channel channel);

    void channelClosed(Channel channel);

    void channelStateChanged(Channel channel);
}
