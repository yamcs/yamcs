package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ChannelException;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryExtract;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.Value;

/**
 * Provides realtime command history subscription via web.
 */
public class CommandHistoryClient implements CommandHistoryConsumer {
    Channel channel;
    Logger log;
    WebSocketServerHandler wsHandler;

    public CommandHistoryClient(Channel channel, WebSocketServerHandler wsHandler) {
        this.channel = channel;
        this.wsHandler = wsHandler;
        log = LoggerFactory.getLogger(CommandHistoryClient.class.getName() + "[" + channel.getInstance() + "]");
    }

    /**
     * called when the socket is closed
     */
    public void quit() {
        // TODO
    }
    

    public void switchChannel(Channel c) throws ChannelException {
        // TODO
    }

    @Override
    public void addedCommand(PreparedCommand pc) {

    }

    @Override
    public void commandHistoryDelivery(CommandHistoryExtract extract) {

    }

    @Override
    public void updatedCommand(CommandId cmdId, long changeDate, String key, Value value) {

    }
}
