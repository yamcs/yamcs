package org.yamcs.web.websocket;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Web.ConnectionInfo;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance.InstanceState;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;

import com.google.protobuf.Message;

/**
 * Runs on the server side and oversees the life cycle of a client web socket connection. Combines multiple types of
 * subscriptions to keep them bundled as one client session.
 */
public class ConnectedWebSocketClient extends ConnectedClient implements ManagementListener {

    private static final Logger log = LoggerFactory.getLogger(ConnectedWebSocketClient.class);

    private List<WebSocketResource> resources = new CopyOnWriteArrayList<>();
    private WebSocketFrameHandler wsHandler;

    public ConnectedWebSocketClient(User user, String applicationName, Processor processor,
            WebSocketFrameHandler wsHandler) {
        super(user, applicationName, processor);
        this.wsHandler = wsHandler;

        // Built-in resources, we could consider moving this to services so that
        // they register their endpoint themselves.
        registerResource(AlarmResource.RESOURCE_NAME, new AlarmResource(this));
        registerResource(CommandHistoryResource.RESOURCE_NAME, new CommandHistoryResource(this));
        registerResource(CommandQueueResource.RESOURCE_NAME, new CommandQueueResource(this));
        registerResource(EventResource.RESOURCE_NAME, new EventResource(this));
        registerResource(InstanceResource.RESOURCE_NAME, new InstanceResource(this));
        registerResource(LinkResource.RESOURCE_NAME, new LinkResource(this));
        registerResource(ManagementResource.RESOURCE_NAME, new ManagementResource(this));
        registerResource(PacketResource.RESOURCE_NAME, new PacketResource(this));
        registerResource(ParameterResource.RESOURCE_NAME, new ParameterResource(this));
        registerResource(ProcessorResource.RESOURCE_NAME, new ProcessorResource(this));
        registerResource(StreamResource.RESOURCE_NAME, new StreamResource(this));
        registerResource(TimeResource.RESOURCE_NAME, new TimeResource(this));

        for (WebSocketResourceProvider provider : ServiceLoader.load(WebSocketResourceProvider.class)) {
            WebSocketResource resource = provider.createForClient(this);
            registerResource(provider.getRoute(), resource);
        }
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
        sendConnectionInfo();
    }

    private void registerResource(String route, WebSocketResource resource) {
        wsHandler.addResource(route, resource);
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
        ManagementService managementService = ManagementService.getInstance();
        managementService.unregisterClient(getId());
        managementService.removeManagementListener(this);
        resources.forEach(WebSocketResource::socketClosed);
    }

    @Override
    public void instanceStateChanged(YamcsServerInstance ysi) {
        String instanceName = ysi.getName();
        // if the client is not connected to this instance we ignore the message
        Processor processor = getProcessor();
        if (processor == null || !processor.getInstance().equals(instanceName)) {
            return;
        }

        if (ysi.getState() == InstanceState.RUNNING) {
            // this means that the instance has just re-started, need to move over to the new processor
            // currently we take the first processor (probably realtime).
            // maybe we should try to switch to one of the same name like the previous one
            processor = Processor.getFirstProcessor(instanceName);
            if (processor == null) {
                log.error("No processor for newly created instance {} ", instanceName);
            } else {
                try {
                    ManagementService.getInstance().connectToProcessor(processor, getId());
                } catch (YamcsException e) {
                    log.error("Error when switching client to new instance {} processor {} ", instanceName,
                            processor.getName(), e);
                }
            }
        }
        sendConnectionInfo();
    }

    void sendConnectionInfo() {
        ConnectionInfo.Builder conninf = ConnectionInfo.newBuilder()
                .setClientId(getId());

        Processor processor = getProcessor();
        if (processor != null) {
            String instanceName = processor.getInstance();
            YamcsServerInstance ysi = YamcsServer.getInstance(instanceName);
            YamcsInstance yi = YamcsInstance.newBuilder().setName(instanceName)
                    .setState(ysi.getState()).build();
            conninf.setInstance(yi);
            conninf.setProcessor(ManagementGpbHelper.toProcessorInfo(processor));
        }

        wsHandler.sendData(ProtoDataType.CONNECTION_INFO, conninf.build());
    }

    public void checkSystemPrivilege(int requestId, SystemPrivilege systemPrivilege) throws WebSocketException {
        if (!getUser().hasSystemPrivilege(systemPrivilege)) {
            throw new WebSocketException(requestId, "Need " + systemPrivilege + " privilege for this operation");
        }
    }
}
