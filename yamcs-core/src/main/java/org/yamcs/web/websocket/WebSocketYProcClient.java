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
public class WebSocketYProcClient implements YProcessorClient {

    private final Logger log;
    private final int clientId;
    private final String applicationName;
    private String username = "unknown";

    private AuthenticationToken authToken = null;

    private final ParameterClient paraClient;
    private final CommandHistoryClient cmdhistClient;
    private final ManagementClient mgmtClient;
   
    public WebSocketYProcClient(String yamcsInstance, WebSocketServerHandler wsHandler, String applicationName, AuthenticationToken authToken) {
        this.applicationName = applicationName;
        log = LoggerFactory.getLogger(WebSocketYProcClient.class.getName() + "[" + yamcsInstance + "]");
        YProcessor yproc = YProcessor.getInstance(yamcsInstance, "realtime");
        
        clientId = ManagementService.getInstance().registerClient(yamcsInstance, yproc.getName(), this);
        paraClient = new ParameterClient(yproc, wsHandler);
        cmdhistClient = new CommandHistoryClient(yproc, wsHandler);
        mgmtClient = new ManagementClient(yproc, wsHandler, clientId);
        this.authToken = authToken;
        this.username = authToken != null ? authToken.getPrincipal().toString() : "unknown";
    }

    @Override
    public void switchYProcessor(YProcessor c, AuthenticationToken authToken) throws YProcessorException {
        log.info("switching yprocessor to {}", c);
        try {
            paraClient.switchYProcessor(c, authToken);
        } catch (NoPermissionException e) {
            throw new YProcessorException("No permission", e);
        }
        cmdhistClient.switchYProcessor(c);

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
        paraClient.quit();
        cmdhistClient.quit();
        mgmtClient.quit();
    }

   
}