package org.yamcs.web.websocket;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorClient;
import org.yamcs.YProcessorException;
import org.yamcs.management.ManagementService;
import org.yamcs.security.AuthenticationToken;

/**
 * Oversees the life cycle of a client web socket connection to a YProcessor. Combines multiple types of subscriptions
 * to keep them bundled as one client session.
 */
public class WebSocketProcessorClient implements YProcessorClient {

    private final Logger log;
    private final int clientId;
    private final String applicationName;
    private String username;

    private AuthenticationToken authToken = null;

    private List<AbstractWebSocketResource> resources = new ArrayList<>();
    
    public WebSocketProcessorClient(String yamcsInstance, WebSocketServerHandler wsHandler, String applicationName, AuthenticationToken authToken) {
        this.applicationName = applicationName;
        this.authToken = authToken;
        this.username = authToken != null ? authToken.getPrincipal().toString() : ManagementService.ANONYMOUS;
        log = LoggerFactory.getLogger(WebSocketProcessorClient.class.getName() + "[" + yamcsInstance + "]");
        YProcessor yproc = YProcessor.getInstance(yamcsInstance, "realtime");
        
        clientId = ManagementService.getInstance().registerClient(yamcsInstance, yproc.getName(), this);
        resources.add(new ParameterResource(yproc, wsHandler));
        resources.add(new CommandHistoryResource(yproc, wsHandler));
        resources.add(new ManagementResource(yproc, wsHandler, clientId));
        resources.add(new AlarmResource(yproc, wsHandler));
        resources.add(new EventResource(yproc, wsHandler));
        resources.add(new StreamResource(yproc, wsHandler));
        resources.add(new TimeResource(yproc, wsHandler));
        resources.add(new LinkResource(yproc, wsHandler));
        resources.add(new CommandQueueResource(yproc, wsHandler));
    }

    @Override
    public void switchYProcessor(YProcessor newProcessor, AuthenticationToken authToken) throws YProcessorException {
        log.info("switching yprocessor to {}", newProcessor);
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
