package org.yamcs.http.websocket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.User;

import com.google.protobuf.Message;

/**
 * Runs on the server side and oversees the life cycle of a client web socket connection. Combines multiple types of
 * subscriptions to keep them bundled as one client session.
 */
public class ConnectedWebSocketClient extends ConnectedClient {

    private static final Logger log = LoggerFactory.getLogger(ConnectedWebSocketClient.class);

    private List<WebSocketResource> resources = new CopyOnWriteArrayList<>();
    private LegacyWebSocketFrameHandler wsHandler;

    public ConnectedWebSocketClient(User user, String applicationName, String address, Processor processor,
            LegacyWebSocketFrameHandler wsHandler) {
        super(user, applicationName, address, processor);
        this.wsHandler = wsHandler;
        addResource(new ParameterResource(this));
    }

    @Override
    public void setProcessor(Processor newProcessor) throws ProcessorException {
        log.info("Switching client {} to processor {}/{}", getId(), newProcessor.getInstance(), newProcessor.getName());
        Processor oldProcessor = getProcessor();
        super.setProcessor(newProcessor);

        for (WebSocketResource resource : resources) {
            if (oldProcessor != null) {
                resource.unselectProcessor();
            }
            if (newProcessor != null) {
                resource.selectProcessor(newProcessor);
            }
        }
    }

    private void addResource(WebSocketResource resource) {
        wsHandler.addResource(resource.getName(), resource);
        resources.add(resource);
    }

    public void sendReply(WebSocketReply reply) {
        wsHandler.sendReply(reply);
    }

    public <T extends Message> void sendData(ProtoDataType dataType, T data) {
        wsHandler.sendData(dataType, data);
    }

    @Override
    public void processorQuit() {
    }

    public void socketClosed() {
        resources.forEach(WebSocketResource::socketClosed);
    }
}
