package org.yamcs.ui;

import org.yamcs.protobuf.YamcsManagement.ChannelInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;



public interface ChannelListener {

    public void log(String text);

    public void popup(String text);

    public void channelUpdated(ChannelInfo ci);
    public void channelClosed(ChannelInfo ci);
    public void clientDisconnected(ClientInfo ci);
    public void clientUpdated(ClientInfo ci);
    public void updateStatistics(Statistics s);
}
