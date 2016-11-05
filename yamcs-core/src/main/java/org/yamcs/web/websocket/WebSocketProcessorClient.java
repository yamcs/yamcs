package org.yamcs.web.websocket;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.yamcs.YProcessor;
import org.yamcs.ProcessorClient;
import org.yamcs.ProcessorException;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;

/**
 * Runs on the server side and oversees the life cycle of a client web socket connection to a Processor. Combines multiple types of subscriptions
 * to keep them bundled as one client session.
 * 
 */
public class WebSocketProcessorClient implements ProcessorClient {

    private final Logger log;
    private final int clientId;
    private final String applicationName;
    private String username;

    private AuthenticationToken authToken = null;

    private List<AbstractWebSocketResource> resources = new ArrayList<>();

    public WebSocketProcessorClient(String yamcsInstance, WebSocketFrameHandler wsHandler, String applicationName, AuthenticationToken authToken) {
        this.applicationName = applicationName;
        this.authToken = authToken;
        this.username = authToken != null ? authToken.getPrincipal().toString() : Privilege.getDefaultUser();
        log = YamcsServer.getLogger(WebSocketProcessorClient.class, yamcsInstance);
        YProcessor processor = YProcessor.getFirstProcessor(yamcsInstance);

        clientId = ManagementService.getInstance().registerClient(yamcsInstance, processor.getName(), this);
        resources.add(new ParameterResource(processor, wsHandler));
        resources.add(new CommandHistoryResource(processor, wsHandler));
        resources.add(new ManagementResource(processor, wsHandler, clientId));
        resources.add(new AlarmResource(processor, wsHandler));
        resources.add(new EventResource(processor, wsHandler));
        resources.add(new StreamResource(processor, wsHandler));
        resources.add(new TimeResource(processor, wsHandler));
        resources.add(new LinkResource(processor, wsHandler));
        resources.add(new CommandQueueResource(processor, wsHandler));
        resources.add(new PacketResource(processor, wsHandler));
    }

    @Override
    public void switchProcessor(YProcessor newProcessor, AuthenticationToken authToken) throws ProcessorException {
        log.info("Switching processor to {}", newProcessor);
        for (AbstractWebSocketResource resource : resources) {
            resource.switchYProcessor(newProcessor, authToken);
        }
    }

    public int getClientId() {
        return clientId;
    }

    public AuthenticationToken getAuthToken() {
        return authToken;
    }

    @Override
    public void yProcessorQuit() {
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
        resources.forEach(r -> r.quit());
    }
}
