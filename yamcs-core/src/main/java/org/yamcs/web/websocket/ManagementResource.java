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
public class ManagementResource extends AbstractWebSocketResource implements ManagementListener {

    public static final String RESOURCE_NAME = "management";

    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private boolean emitClientInfo;
    private boolean emitProcessorStatistics;

    public ManagementResource(ConnectedWebSocketClient client) {
        super(client);
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        case OP_unsubscribe:
            return processUnsubscribeRequest(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    /**
     * Registers for updates on any processor or client. Sends the current set of processor, and clients (in that order)
     * to the requester.
     * <p>
     * Calling this multiple times, will cause the current set of data to be sent again. Further updates will still
     * arrive one-time only.
     */
    private WebSocketReply processSubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        emitClientInfo = true;
        emitProcessorStatistics = true;
        if (ctx.getData() != null) {
            ManagementSubscriptionRequest req = decoder
                    .decodeMessageData(ctx, ManagementSubscriptionRequest.newBuilder()).build();
            emitClientInfo = !req.hasClientInfo() || req.getClientInfo();
            emitProcessorStatistics = !req.hasProcessorStatistics() || req.getProcessorStatistics();
        }

        wsHandler.sendReply(WebSocketReply.ack(ctx.getRequestId()));

        // Send current set of clients
        if (emitClientInfo) {
            Set<ConnectedClient> clients = ManagementService.getInstance().getClients();
            for (ConnectedClient client : clients) {
                ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED);
                clientInfo = ClientInfo.newBuilder(clientInfo)
                        .setCurrentClient(clientInfo.getId() == client.getId())
                        .build();
                wsHandler.sendData(ProtoDataType.CLIENT_INFO, clientInfo);
            }
        }
        ManagementService.getInstance().addManagementListener(this);
        return null;
    }

    private WebSocketReply processUnsubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        ManagementService.getInstance().removeManagementListener(this);
        wsHandler.sendReply(new WebSocketReply(ctx.getRequestId()));
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
    public void clientRegistered(ConnectedClient client) {
        if (emitClientInfo) {
            ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED);
            clientInfo = ClientInfo.newBuilder(clientInfo)
                    .setCurrentClient(client.getId() == clientInfo.getId())
                    .build();
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, clientInfo);
        }
    }

    @Override
    public void clientInfoChanged(ConnectedClient client) {
        if (emitClientInfo) {
            ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED);
            clientInfo = ClientInfo.newBuilder(clientInfo)
                    .setCurrentClient(client.getId() == clientInfo.getId())
                    .build();
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, clientInfo);
        }
    }

    @Override
    public void clientUnregistered(ConnectedClient client) {
        if (this.client == client) {
            return;
        }

        if (emitClientInfo) {
            ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(client, ClientState.DISCONNECTED);
            clientInfo = ClientInfo.newBuilder(clientInfo).setCurrentClient(false).build();
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, clientInfo);
        }
    }

    @Override
    public void statisticsUpdated(Processor processor, Statistics stats) {
        if (emitProcessorStatistics) {
            wsHandler.sendData(ProtoDataType.PROCESSING_STATISTICS, stats);
        }
    }

    @Override
    public void socketClosed() {
        ManagementService.getInstance().removeManagementListener(this);
    }
}
