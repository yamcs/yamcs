package org.yamcs.web.websocket;

import java.util.List;
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
import org.yamcs.web.HttpServer;

import com.google.protobuf.Message;

/**
 * Runs on the server side and oversees the life cycle of a client web socket connection. Combines multiple types of
 * subscriptions to keep them bundled as one client session.
 */
public class ConnectedWebSocketClient extends ConnectedClient implements ManagementListener {

    private static final Logger log = LoggerFactory.getLogger(ConnectedWebSocketClient.class);

    private List<WebSocketResource> resources = new CopyOnWriteArrayList<>();
    private WebSocketFrameHandler wsHandler;

    public ConnectedWebSocketClient(User user, String applicationName, String address, Processor processor,
            WebSocketFrameHandler wsHandler) {
        super(user, applicationName, address, processor);
        this.wsHandler = wsHandler;

        addResource(new AlarmResource(this));
        addResource(new CommandHistoryResource(this));
        addResource(new CommandQueueResource(this));
        addResource(new EventResource(this));
        addResource(new InstanceResource(this));
        addResource(new LinkResource(this));
        addResource(new ManagementResource(this));
        addResource(new PacketResource(this));
        addResource(new ParameterResource(this));
        addResource(new ProcessorResource(this));
        addResource(new StreamResource(this));
        addResource(new StreamsResource(this));
        addResource(new TimeResource(this));

        for (HttpServer httpServer : YamcsServer.getServer().getGlobalServices(HttpServer.class)) {
            httpServer.getWebSocketExtensions().forEach(supplier -> {
                WebSocketResource resource = supplier.apply(this);
                addResource(resource);
            });
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

        if (ysi.state() == InstanceState.RUNNING) {
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
            YamcsServerInstance ysi = YamcsServer.getServer().getInstance(instanceName);
            YamcsInstance yi = YamcsInstance.newBuilder().setName(instanceName)
                    .setState(ysi.state()).build();
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
