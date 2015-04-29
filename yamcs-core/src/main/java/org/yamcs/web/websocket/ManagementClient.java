package org.yamcs.web.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.management.HornetProcessorManagement;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;

public class ManagementClient extends AbstractWebSocketResource {
    public static final String OP_getProcessorInfo = "getProcessorInfo";
    public static final String OP_getClientInfo = "getClientInfo";
    Logger log;
    int clientId;
    
    public ManagementClient(YProcessor yproc, WebSocketServerHandler wsHandler, int clientId) {
        super(yproc, wsHandler);
        wsHandler.addResource("management", this);
        log = LoggerFactory.getLogger(ParameterClient.class.getName() + "[" + yproc.getInstance() + "]");
        this.clientId = clientId;
    }


    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_getProcessorInfo:
            return processGetProcessorInfoRequest(ctx, decoder);
        case OP_getClientInfo:
            return processGetClientInfoRequest(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }
    
    private WebSocketReplyData processGetProcessorInfoRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        int requestId = ctx.getRequestId();
        ProcessorInfo pinfo = HornetProcessorManagement.getProcessorInfo(yproc);
        try {
            wsHandler.sendReply(toAckReply(requestId));
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, pinfo, SchemaYamcsManagement.ProcessorInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        return null;
    }
    
    //return client info of this client
    private WebSocketReplyData processGetClientInfoRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        int requestId = ctx.getRequestId();
        ClientInfo cinfo = ManagementService.getInstance().getClientInfo(clientId);
        try {
            wsHandler.sendReply(toAckReply(requestId));
            wsHandler.sendData(ProtoDataType.CLIENT_INFO, cinfo, SchemaYamcsManagement.ClientInfo.WRITE);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        return null;
    }


    public void quit() {
    }
}
