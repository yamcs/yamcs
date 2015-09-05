package org.yamcs.api.ws;

import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

public interface WebSocketClientCallback {

    
    /**
     * When the connection was successfully established
     */
    default void connected() {}
    
    /**
     * When the initial connection attempt failed
     */
    default void connectionFailed(Throwable t) {}
    
    /**
     * When a previously successfulconnection was disconnected
     */
    default void disconnected() {}
    
    // TODO get this out of here and into the WebSocketResponseHandler
    default void onInvalidIdentification(NamedObjectId id) {}
    
    void onMessage(WebSocketSubscriptionData data);
}
