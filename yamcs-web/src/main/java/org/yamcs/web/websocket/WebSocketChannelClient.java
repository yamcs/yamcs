package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ChannelClient;
import org.yamcs.ChannelException;
import org.yamcs.management.ManagementService;

/**
 * Oversees the life cycle of a client web socket connection to a channel. Combines multiple types of subscriptions
 * to keep them bundled as one client session.
 */
public class WebSocketChannelClient implements ChannelClient {

    private final Logger log;
    private final int clientId;
    private final String username = "unknown";
    private final String applicationName;

    private final ParameterClient paraClient;
    private final CommandHistoryClient cmdhistClient;

    public WebSocketChannelClient(String yamcsInstance, WebSocketServerHandler wsHandler, String applicationName) {
        this.applicationName = applicationName;
        log = LoggerFactory.getLogger(WebSocketChannelClient.class.getName() + "[" + yamcsInstance + "]");

        Channel channel = Channel.getInstance(yamcsInstance, "realtime");
        clientId = ManagementService.getInstance().registerClient(yamcsInstance, channel.getName(), this);
        paraClient = new ParameterClient(channel, wsHandler);
        cmdhistClient = new CommandHistoryClient(channel, wsHandler);
    }

    public ParameterClient getParameterClient() {
        return paraClient;
    }

    public CommandHistoryClient getCommandHistoryClient() {
        return cmdhistClient;
    }

    @Override
    public void switchChannel(Channel c) throws ChannelException {
        log.info("switching channel to {}", c);
        paraClient.switchChannel(c);
        cmdhistClient.switchChannel(c);
    }

    @Override
    public void channelQuit() {
        // TODO Auto-generated method stub
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Called when the socket is closed.
     */
    public void quit() {
        ManagementService.getInstance().unregisterClient(clientId);
        paraClient.quit();
        cmdhistClient.quit();
    }
}
