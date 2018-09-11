package org.yamcs.web.websocket;

public interface WebSocketResourceProvider {

    public String getRoute();

    public WebSocketResource createForClient(ConnectedWebSocketClient client);

}
