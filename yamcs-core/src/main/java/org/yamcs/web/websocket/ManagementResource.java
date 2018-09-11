package org.yamcs.web.websocket;

import java.util.Set;

import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Web.ManagementSubscriptionRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.web.rest.YamcsToGpbAssembler;

/**
 * Provides access to any Processor/Client info over web socket
 */
public class ManagementResource implements WebSocketResource, ManagementListener {

    public static final String RESOURCE_NAME = "management";

    private ConnectedWebSocketClient client;

    private boolean emitClientInfo;
    private boolean emitProcessorStatistics;

    public ManagementResource(ConnectedWebSocketClient client) {
        this.client = client;
    }

    /**
     * Registers for updates on any processor or client. Sends the current set of processor, and clients (in that order)
     * to the requester.
     * <p>
     * Calling this multiple times, will cause the current set of data to be sent again. Further updates will still
     * arrive one-time only.
     */
    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        emitClientInfo = true;
        emitProcessorStatistics = true;
        if (ctx.getData() != null) {
            ManagementSubscriptionRequest req = decoder
                    .decodeMessageData(ctx, ManagementSubscriptionRequest.newBuilder()).build();
            emitClientInfo = !req.hasClientInfo() || req.getClientInfo();
            emitProcessorStatistics = !req.hasProcessorStatistics() || req.getProcessorStatistics();
        }

        client.sendReply(WebSocketReply.ack(ctx.getRequestId()));

        // Send current set of clients
        if (emitClientInfo) {
            Set<ConnectedClient> clients = ManagementService.getInstance().getClients();
            for (ConnectedClient otherClient : clients) {
                ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(otherClient, ClientState.CONNECTED);
                client.sendData(ProtoDataType.CLIENT_INFO, clientInfo);
            }
        }
        ManagementService.getInstance().addManagementListener(this);
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        ManagementService.getInstance().removeManagementListener(this);
        client.sendReply(new WebSocketReply(ctx.getRequestId()));
        return null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        // Ignore
    }

    @Override
    public void unselectProcessor() {
        // Ignore
    }

    @Override
    public void processorAdded(ProcessorInfo processorInfo) {
    }

    @Override
    public void processorStateChanged(ProcessorInfo processorInfo) {
    }

    @Override
    public void processorClosed(ProcessorInfo processorInfo) {
    }

    @Override
    public void clientRegistered(ConnectedClient newClient) {
        if (emitClientInfo) {
            ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(newClient, ClientState.CONNECTED);
            client.sendData(ProtoDataType.CLIENT_INFO, clientInfo);
        }
    }

    @Override
    public void clientInfoChanged(ConnectedClient changedClient) {
        if (emitClientInfo) {
            ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(changedClient, ClientState.CONNECTED);
            client.sendData(ProtoDataType.CLIENT_INFO, clientInfo);
        }
    }

    @Override
    public void clientUnregistered(ConnectedClient oldClient) {
        if (this.client == oldClient) {
            return;
        }

        if (emitClientInfo) {
            ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(oldClient, ClientState.DISCONNECTED);
            client.sendData(ProtoDataType.CLIENT_INFO, clientInfo);
        }
    }

    @Override
    public void statisticsUpdated(Processor processor, Statistics stats) {
        if (emitProcessorStatistics) {
            client.sendData(ProtoDataType.PROCESSING_STATISTICS, stats);
        }
    }

    @Override
    public void socketClosed() {
        ManagementService.getInstance().removeManagementListener(this);
    }
}
