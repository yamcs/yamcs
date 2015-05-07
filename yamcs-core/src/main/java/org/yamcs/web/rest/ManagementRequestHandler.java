package org.yamcs.web.rest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.RestConnectToProcessorResponse;
import org.yamcs.protobuf.Rest.RestCreateProcessorResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;

public class ManagementRequestHandler extends AbstractRestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(ArchiveRequestHandler.class.getName());
    
    
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, String remainingUri, AuthenticationToken authToken) throws RestException {
        String[] path = remainingUri.split("/", 2);
        
        if (path.length == 0) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
        if("processor".equals(qsDecoder.path())) {
            handleProcessorManagementRequest(ctx, req, yamcsInstance, qsDecoder, authToken);
        } else {
            log.warn("Unknown request {} has been received");
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
    }
        
    private void handleProcessorManagementRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance,  QueryStringDecoder qsDecoder, AuthenticationToken authToken) throws RestException {
        ProcessorManagementRequest yprocReq = readMessage(req, SchemaYamcsManagement.ProcessorManagementRequest.MERGE).build();
        if(req.getMethod()==HttpMethod.POST) {
            switch(yprocReq.getOperation()) {
            case CONNECT_TO_PROCESSOR:
                ManagementService mservice = ManagementService.getInstance();
                try {
                    mservice.connectToProcessor(yprocReq, authToken);
                    RestConnectToProcessorResponse response = RestConnectToProcessorResponse.newBuilder().build();
                    writeMessage(ctx, req, qsDecoder, response, SchemaRest.RestConnectToProcessorResponse.WRITE);
                } catch (YamcsException e) {
                    throw new BadRequestException(e.getMessage());
                }
                break;
            
            case CREATE_PROCESSOR:
                mservice = ManagementService.getInstance();
                try {
                    mservice.createProcessor(yprocReq, authToken);
                    RestCreateProcessorResponse response = RestCreateProcessorResponse.newBuilder().build();
                    writeMessage(ctx, req, qsDecoder, response, SchemaRest.RestCreateProcessorResponse.WRITE);
                } catch (YamcsException e) {
                    throw new BadRequestException(e.getMessage());
                }
                break;
            
            default:
                throw new BadRequestException("Invalid operation "+yprocReq.getOperation()+" specified");
            }
        }
    }
}
