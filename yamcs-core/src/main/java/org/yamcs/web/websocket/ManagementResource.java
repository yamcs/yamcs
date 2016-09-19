package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.security.AuthenticationToken;

/**
 * Provides access to any Processor/Client info over web socket
 */
public class ManagementResource extends AbstractWebSocketResource implements ManagementListener {

    private static final Logger log = LoggerFactory.getLogger(ManagementResource.class);

    public static final String RESOURCE_NAME = "management";
    
    public static final String OP_getProcessorInfo = "getProcessorInfo";
    public static final String OP_getClientInfo = "getClientInfo";
    public static final String OP_subscribe = "subscribe";

    private int clientId;

    public ManagementResource(YProcessor yproc, WebSocketFrameHandler wsHandler, int clientId) {
        super(yproc, wsHandler);
        wsHandler.addResource(RESOURCE_NAME, this);
        this.clientId = clientId;
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authenticationToken) throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_getProcessorInfo:
            return processGetProcessorInfoRequest(ctx, decoder);
        case OP_getClientInfo:
            return processGetClientInfoRequest(ctx, decoder);
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private WebSocketReplyData processGetProcessorInfoRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        int requestId = ctx.getRequestId();
        ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
        try {
            wsHandler.sendReply(toAckReply(requestId));
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, pinfo, SchemaYamcsManagement.ProcessorInfo.WRITE);
        } catch (IOException e) {
            log.warn("Exception when sending data", e);
            return null;
        }
        return null;
    }

    //return client info of this client
    private WebSocketReplyData processGetClientInfoRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        int requestId = ctx.getRequestId();
        ClientInfo cinfo = ManagementService.getInstance().getClientInfo(clientId);
        cinfo = ClientInfo.newBuilder(cinfo).setState(ClientState.CONNECTED).setCurrentClient(true).build();
        try {
            wsHandler.sendReply(toAckReply(requestId));
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo, SchemaYamcsManagement.ClientInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        return null;
    }

    /**
     * Registers for updates on any processor or client. Sends the current set
     * of processor, and clients (in that order) to the requester.
     * <p>
     * Calling this multiple times, will cause the current set of data to be
     * sent again. Further updates will still arrive one-time only.
     */
    private WebSocketReplyData processSubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        try {
            wsHandler.sendReply(toAckReply(ctx.getRequestId()));

            // Send current set of processors
            for (YProcessor processor : YProcessor.getProcessors()) {
                ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
                wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, pinfo, SchemaYamcsManagement.ProcessorInfo.WRITE);
            }

            // Send current set of clients
            Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
            for (ClientInfo client : clients) {
                ClientInfo cinfo = ClientInfo.newBuilder(client)
                        .setState(ClientState.CONNECTED).setCurrentClient(client.getId() == clientId).build();
                wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo, SchemaYamcsManagement.ClientInfo.WRITE);
            }
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        ManagementService.getInstance().addManagementListener(this);
        return null;
    }

    @Override
    public void quit() {
        ManagementService.getInstance().removeManagementListener(this);
    }

    @Override
    public void processorAdded(ProcessorInfo processorInfo) {
        try {
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo, SchemaYamcsManagement.ProcessorInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void processorStateChanged(ProcessorInfo processorInfo) {
        try {
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo, SchemaYamcsManagement.ProcessorInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void processorClosed(ProcessorInfo processorInfo) {
        try {
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo, SchemaYamcsManagement.ProcessorInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void clientRegistered(ClientInfo ci) {
        ClientInfo cinfo = ClientInfo.newBuilder(ci).setState(ClientState.CONNECTED).setCurrentClient(ci.getId() == clientId).build();
        try {
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo, SchemaYamcsManagement.ClientInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void clientInfoChanged(ClientInfo ci) {
        ClientInfo cinfo = ClientInfo.newBuilder(ci).setState(ClientState.CONNECTED).setCurrentClient(ci.getId() == clientId).build();
        try {
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo, SchemaYamcsManagement.ClientInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void clientUnregistered(ClientInfo ci) {
        if (ci.getId() == clientId) return;

        ClientInfo cinfo = ClientInfo.newBuilder(ci).setState(ClientState.DISCONNECTED).setCurrentClient(false).build();
        try {
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo, SchemaYamcsManagement.ClientInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void statisticsUpdated(YProcessor processor, Statistics stats) {
        try {
            wsHandler.sendData(ProtoDataType.PROCESSING_STATISTICS, stats, SchemaYamcsManagement.Statistics.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }
}
