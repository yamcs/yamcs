package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.NoPermissionException;
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
    private String username = "unknown";

    private AuthenticationToken authToken = null;

    private final ParameterResource paraResource;
    private final CommandHistoryResource cmdhistResource;
    private final ManagementResource mgmtResource;
    private final AlarmsResource alarmResource;
    private final StreamResource streamResource;
   
    public WebSocketProcessorClient(String yamcsInstance, WebSocketServerHandler wsHandler, String applicationName, AuthenticationToken authToken) {
        this.applicationName = applicationName;
        this.authToken = authToken;
        this.username = authToken != null ? authToken.getPrincipal().toString() : "unknown";
        log = LoggerFactory.getLogger(WebSocketProcessorClient.class.getName() + "[" + yamcsInstance + "]");
        YProcessor yproc = YProcessor.getInstance(yamcsInstance, "realtime");
        
        clientId = ManagementService.getInstance().registerClient(yamcsInstance, yproc.getName(), this);
        paraResource = new ParameterResource(yproc, wsHandler);
        cmdhistResource = new CommandHistoryResource(yproc, wsHandler);
        mgmtResource = new ManagementResource(yproc, wsHandler, clientId);
        alarmResource = new AlarmsResource(yproc, wsHandler);
        streamResource = new StreamResource(yproc, wsHandler);
    }

    @Override
    public void switchYProcessor(YProcessor newProcessor, AuthenticationToken authToken) throws YProcessorException {
        log.info("switching yprocessor to {}", newProcessor);
        try {
            paraResource.switchYProcessor(newProcessor, authToken);
        } catch (NoPermissionException e) {
            throw new YProcessorException("No permission", e);
        }
        cmdhistResource.switchYProcessor(newProcessor);
        alarmResource.switchYProcessor(newProcessor);
        streamResource.switchYProcessor(newProcessor);
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
        paraResource.quit();
        cmdhistResource.quit();
        mgmtResource.quit();
        alarmResource.quit();
        streamResource.quit();
    }
}
