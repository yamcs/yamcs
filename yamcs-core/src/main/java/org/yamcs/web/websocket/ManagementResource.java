package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Web.ManagementSubscriptionRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

/**
 * Provides access to any Processor/Client info over web socket
 */
public class ManagementResource extends AbstractWebSocketResource implements ManagementListener {

    private static final Logger log = LoggerFactory.getLogger(ManagementResource.class);
    public static final String RESOURCE_NAME = "management";

    public static final String OP_getProcessorInfo = "getProcessorInfo";
    public static final String OP_getClientInfo = "getClientInfo";
    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private int clientId;
    private boolean emitClientInfo;
    private boolean emitProcessorInfo;
    private boolean emitProcessorStatistics;

    public ManagementResource(WebSocketClient client) {
        super(client);
        clientId = client.getClientId();
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_getProcessorInfo:
            return processGetProcessorInfoRequest(ctx, decoder);
        case OP_getClientInfo:
            return processGetClientInfoRequest(ctx, decoder);
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        case OP_unsubscribe:
            return processUnsubscribeRequest(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private WebSocketReply processGetProcessorInfoRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        int requestId = ctx.getRequestId();
        ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
        try {
            WebSocketReply reply = new WebSocketReply(requestId);
            reply.attachData("ProcessorInfo", pinfo);
            wsHandler.sendReply(reply);

            // TODO Should probably remove this line, now that we sent this already in the response.
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, pinfo);
        } catch (IOException e) {
            log.warn("Exception when sending data", e);
            return null;
        }
        return null;
    }

    // return client info of this client
    private WebSocketReply processGetClientInfoRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        int requestId = ctx.getRequestId();
        ClientInfo cinfo = ManagementService.getInstance().getClientInfo(clientId);
        cinfo = ClientInfo.newBuilder(cinfo).setState(ClientState.CONNECTED).setCurrentClient(true).build();
        try {
            WebSocketReply reply = new WebSocketReply(requestId);
            reply.attachData("ClientInfo", cinfo);
            wsHandler.sendReply(reply);

            // TODO Should probably remove this line, now that we sent this already in the response.
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        return null;
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
        emitProcessorInfo = true;
        emitProcessorStatistics = true;
        if (ctx.getData() != null) {
            ManagementSubscriptionRequest req = decoder
                    .decodeMessageData(ctx, ManagementSubscriptionRequest.newBuilder()).build();
            emitClientInfo = !req.hasClientInfo() || req.getClientInfo();
            emitProcessorInfo = !req.hasProcessorInfo() || req.getProcessorInfo();
            emitProcessorStatistics = !req.hasProcessorStatistics() || req.getProcessorStatistics();
        }
        try {
            wsHandler.sendReply(WebSocketReply.ack(ctx.getRequestId()));

            // Send current set of processors
            if (emitProcessorInfo) {
                for (Processor processor : Processor.getProcessors()) {
                    ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
                    wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, pinfo);
                }
            }

            // Send current set of clients
            if (emitClientInfo) {
                Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
                for (ClientInfo client : clients) {
                    ClientInfo cinfo = ClientInfo.newBuilder(client)
                            .setState(ClientState.CONNECTED).setCurrentClient(client.getId() == clientId).build();
                    wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo);
                }
            }
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        ManagementService.getInstance().addManagementListener(this);
        return null;
    }

    private WebSocketReply processUnsubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        ManagementService.getInstance().removeManagementListener(this);
        try {
            wsHandler.sendReply(new WebSocketReply(ctx.getRequestId()));
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        return null;
    }

    @Override
    public void quit() {
        ManagementService.getInstance().removeManagementListener(this);
    }

    @Override
    public void processorAdded(ProcessorInfo processorInfo) {
        if (emitProcessorInfo) {
            try {
                wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
            } catch (IOException e) {
                log.error("Exception when sending data", e);
            }
        }
    }

    @Override
    public void processorStateChanged(ProcessorInfo processorInfo) {
        if (emitProcessorInfo) {
            try {
                wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
            } catch (IOException e) {
                log.error("Exception when sending data", e);
            }
        }
    }

    @Override
    public void processorClosed(ProcessorInfo processorInfo) {
        if (emitProcessorInfo) {
            try {
                wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
            } catch (IOException e) {
                log.error("Exception when sending data", e);
            }
        }
    }

    @Override
    public void clientRegistered(ClientInfo ci) {
        if (emitClientInfo) {
            ClientInfo cinfo = ClientInfo.newBuilder(ci).setState(ClientState.CONNECTED)
                    .setCurrentClient(ci.getId() == clientId).build();
            try {
                wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo);
            } catch (IOException e) {
                log.error("Exception when sending data", e);
            }
        }
    }

    @Override
    public void clientInfoChanged(ClientInfo ci) {
        if (emitClientInfo) {
            ClientInfo cinfo = ClientInfo.newBuilder(ci).setState(ClientState.CONNECTED)
                    .setCurrentClient(ci.getId() == clientId).build();
            try {
                wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo);
            } catch (IOException e) {
                log.error("Exception when sending data", e);
            }
        }
    }

    @Override
    public void clientUnregistered(ClientInfo ci) {
        if (ci.getId() == clientId) {
            return;
        }

        if (emitClientInfo) {
            ClientInfo cinfo = ClientInfo.newBuilder(ci).setState(ClientState.DISCONNECTED).setCurrentClient(false)
                    .build();
            try {
                wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo);
            } catch (IOException e) {
                log.error("Exception when sending data", e);
            }
        }
    }

    @Override
    public void statisticsUpdated(Processor processor, Statistics stats) {
        if (emitProcessorStatistics) {
            try {
                wsHandler.sendData(ProtoDataType.PROCESSING_STATISTICS, stats);
            } catch (IOException e) {
                log.error("Exception when sending data", e);
            }
        }
    }
}
