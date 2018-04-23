package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.yamcs.Processor;
import org.yamcs.ProcessorClient;
import org.yamcs.ProcessorException;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Web.ConnectionInfo;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.utils.LoggingUtils;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;

/**
 * Runs on the server side and oversees the life cycle of a client web socket connection to a Processor. Combines
 * multiple types of subscriptions to keep them bundled as one client session.
 */
public class WebSocketProcessorClient implements ProcessorClient, ManagementListener {

    private final Logger log;
    private final int clientId;
    private final String applicationName;
    private final String username;
    private final AuthenticationToken authToken;

    private Processor processor;

    private List<AbstractWebSocketResource> resources = new CopyOnWriteArrayList<>();
    private WebSocketFrameHandler wsHandler;

    public WebSocketProcessorClient(String yamcsInstance, WebSocketFrameHandler wsHandler, String applicationName,
            AuthenticationToken authToken) {
        this.applicationName = applicationName;
        this.authToken = authToken;
        this.username = authToken != null ? authToken.getPrincipal().toString()
                : Privilege.getInstance().getDefaultUser();
        this.wsHandler = wsHandler;
        log = LoggingUtils.getLogger(WebSocketProcessorClient.class, yamcsInstance);
        processor = Processor.getFirstProcessor(yamcsInstance);
        ManagementService mgrSrv = ManagementService.getInstance();
        clientId = mgrSrv.registerClient(yamcsInstance, processor.getName(), this);
        mgrSrv.addManagementListener(this);

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
    public void switchProcessor(Processor newProcessor) throws ProcessorException {
        log.info("Switching processor from {}/{} to {}/{}", processor.getInstance(), processor.getName(),
                newProcessor.getInstance(), newProcessor.getName());
        Processor oldProcessor = processor;
        processor = newProcessor;
        for (AbstractWebSocketResource resource : resources) {
            resource.switchProcessor(oldProcessor, newProcessor);
        }
        sendConnectionInfo();
        // Note: We're not updating log and clientId in case of instance change. Maybe that's something we should do
        // though
    }

    @Override
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
        ManagementService mgrSrv = ManagementService.getInstance();
        mgrSrv.unregisterClient(clientId);
        mgrSrv.removeManagementListener(this);
        resources.forEach(r -> r.quit());
    }

    @Override
    public void instanceStateChanged(YamcsServerInstance ysi) {
        String instanceName = ysi.getName();
        // if the client is not connected to this instance we ignore the message
        if (!processor.getInstance().equals(instanceName)) {
            return;
        }
        Service.State state = ysi.state();

        if (state == State.RUNNING) {
            // this means that the instance has just re-started, need to move over to the new processor
            // currently we take the first processor (probably realtime).
            // maybe we should try to switch to one of the same name like the previous one
            processor = Processor.getFirstProcessor(instanceName);
            if (processor == null) {
                log.error("No processor for newly created instance {} ", instanceName);
            } else {
                try {
                    ManagementService.getInstance().connectToProcessor(processor, clientId);
                } catch (YamcsException e) {
                    log.error("Error when switching client to new instance {} processor {} ", instanceName,
                            processor.getName(), e);
                }
            }
        }
        sendConnectionInfo();
    }

    private void sendConnectionInfo() {
        String instanceName = processor.getInstance();
        YamcsServerInstance ysi = YamcsServer.getInstance(instanceName);
        Service.State instanceState = ysi.state();
        YamcsInstance yi = YamcsInstance.newBuilder().setName(instanceName)
                .setState(ServiceState.valueOf(instanceState.name())).build();
        ConnectionInfo.Builder conninf = ConnectionInfo.newBuilder().setInstance(yi);
        conninf.setProcessor(ManagementGpbHelper.toProcessorInfo(processor));
        try {
            wsHandler.sendData(ProtoDataType.CONNECTION_INFO, conninf.build());
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }
}
