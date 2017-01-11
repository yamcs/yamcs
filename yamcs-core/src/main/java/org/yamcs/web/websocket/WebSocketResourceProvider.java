package org.yamcs.web.websocket;

public interface WebSocketResourceProvider {

    public String getRoute();

    public AbstractWebSocketResource createForClient(WebSocketProcessorClient client);

}
