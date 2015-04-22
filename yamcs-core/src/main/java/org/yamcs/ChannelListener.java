package org.yamcs;

public interface ChannelListener {

    void channelAdded(YProcessor channel);

    void channelClosed(YProcessor channel);

    void channelStateChanged(YProcessor channel);
}
