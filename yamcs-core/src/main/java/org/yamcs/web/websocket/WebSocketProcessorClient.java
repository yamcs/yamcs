package org.yamcs.web.websocket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.yamcs.ProcessorClient;
import org.yamcs.ProcessorException;
import org.yamcs.Processor;
import org.yamcs.management.ManagementService;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.utils.LoggingUtils;

/**
 * Runs on the server side and oversees the life cycle of a client web socket connection to a Processor. Combines multiple types of subscriptions
 * to keep them bundled as one client session.
 */
public class WebSocketProcessorClient implements ProcessorClient {

    private final Logger log;
    private final int clientId;
    private final String applicationName;
    private final String username;
    private final AuthenticationToken authToken;

    private Processor processor;

    private List<AbstractWebSocketResource> resources = new CopyOnWriteArrayList<>();
    private WebSocketFrameHandler wsHandler;

    public WebSocketProcessorClient(String yamcsInstance, WebSocketFrameHandler wsHandler, String applicationName, AuthenticationToken authToken) {
        this.applicationName = applicationName;
        this.authToken = authToken;
        this.username = authToken != null ? authToken.getPrincipal().toString() : Privilege.getDefaultUser();
        this.wsHandler = wsHandler;
        log = LoggingUtils.getLogger(WebSocketProcessorClient.class, yamcsInstance);
        processor = Processor.getFirstProcessor(yamcsInstance);

        clientId = ManagementService.getInstance().registerClient(yamcsInstance, processor.getName(), this);

        // Built-in resources, we could consider moving this to services so that
        // they register their endpoint themselves.
        registerResource(ParameterResource.RESOURCE_NAME, new ParameterResource(this));
        registerResource(CommandHistoryResource.RESOURCE_NAME, new CommandHistoryResource(this));
        registerResource(ManagementResource.RESOURCE_NAME, new ManagementResource(this));
        registerResource(AlarmResource.RESOURCE_NAME, new AlarmResource(this));
        registerResource(EventResource.RESOURCE_NAME, new EventResource(this));
        registerResource(StreamResource.RESOURCE_NAME, new StreamResource(this));
        registerResource(TimeResource.RESOURCE_NAME, new TimeResource(this));
        registerResource(LinkResource.RESOURCE_NAME, new LinkResource(this));
        registerResource(CommandQueueResource.RESOURCE_NAME, new CommandQueueResource(this));
        registerResource(PacketResource.RESOURCE_NAME, new PacketResource(this));
    }

    @Override
    public void switchProcessor(Processor newProcessor, AuthenticationToken authToken) throws ProcessorException {
        log.info("Switching processor from {}/{} to {}/{}", processor.getInstance(), processor.getName(), newProcessor.getInstance(), newProcessor.getName());
        Processor oldProcessor = processor;
        processor = newProcessor;
        for (AbstractWebSocketResource resource : resources) {
            resource.switchProcessor(oldProcessor, newProcessor);
        }
        // Note: We're not updating log and clientId in case of instance change. Maybe that's something we should do though
    }

    public Processor getProcessor() {
        return processor;
    }

    public void registerResource(String route, AbstractWebSocketResource resource) {
        wsHandler.addResource(route, resource);
        resources.add(resource);
    }

    public int getClientId() {
        return clientId;
    }

    public AuthenticationToken getAuthToken() {
        return authToken;
    }

    public WebSocketFrameHandler getWebSocketFrameHandler() {
        return wsHandler;
    }

    @Override
    public void processorQuit() {
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
