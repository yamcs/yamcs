package org.yamcs.web.rest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Privilege;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;

public class ProcessorRequestHandler extends AbstractRestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(ArchiveRequestHandler.class.getName());
    
    
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, String remainingUri) throws RestException {
        String[] path = remainingUri.split("/", 2);
        
        if (path.length == 0) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        } 
        String yprocName = path[0];
        YProcessor yproc = YProcessor.getInstance(yamcsInstance, yprocName);
        if(yproc==null) {
            log.warn("Sending NOT_FOUND because invalid processor name '{}' has been requested", yprocName);
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        remainingUri = remainingUri.substring(path.length > 1 ? path[0].length() + 1 : path[0].length());
        ProcessorRequest yprocReq = readMessage(req, SchemaYamcsManagement.ProcessorRequest.MERGE).build();
        if(req.getMethod()==HttpMethod.POST) {
            switch(yprocReq.getOperation()) {
            case RESUME:
                if(!yproc.isReplay()) {
                    throw new BadRequestException("Cannot resume a non replay processor ");
                } 
                yproc.resume();
                break;
            case PAUSE:
                if(!yproc.isReplay()) {
                    throw new BadRequestException("Cannot pause a non replay processor ");
                }
                yproc.pause();
                break;
            case SEEK:
                if(!yproc.isReplay()) {
                    throw new BadRequestException("Cannot seek a non replay processor ");
                }
                if(!yprocReq.hasSeekTime()) {
                    throw new BadRequestException("No seek time specified");                
                }
                yproc.seek(yprocReq.getSeekTime());
                break;
            case CONNECT_TO_PROCESSOR:
                ManagementService mservice = ManagementService.getInstance();
                try {
                    mservice.connectToProcessor(yprocReq, Privilege.getInstance());
                } catch (YamcsException e) {
                    throw new BadRequestException(e.getMessage());
                }
                break;
            
            case CREATE:
                mservice = ManagementService.getInstance();
                try {
                    mservice.createProcessor(yprocReq, Privilege.getInstance());
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
