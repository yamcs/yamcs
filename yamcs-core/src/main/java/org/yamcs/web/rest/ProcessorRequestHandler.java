package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.RestConnectToProcessorResponse;
import org.yamcs.protobuf.Rest.RestCreateProcessorResponse;
import org.yamcs.protobuf.Rest.RestListProcessorsResponse;
import org.yamcs.protobuf.Rest.RestProcessorResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;

public class ProcessorRequestHandler extends AbstractRestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(ProcessorRequestHandler.class.getName());
    
    @Override
    public RestResponse handleRequest(RestRequest req) throws RestException {
        String path = req.getRemainingUri();
        if ("".equals(path)) {
            return handleProcessorManagementRequest(req);
        } else if ("list".equals(path)) {
            return handleProcessorListRequest(req);
        } else {
            String yprocName = path.split("/")[0];
            YProcessor processor = YProcessor.getInstance(req.yamcsInstance, yprocName);
            if(processor==null) {
                log.warn("Sending NOT_FOUND because invalid processor name '{}' has been requested", yprocName);
                throw new NotFoundException(req);
            }
            return handleProcessorRequest(req, processor);
        }
    }

    private RestResponse handleProcessorListRequest(RestRequest req) throws RestException {
        req.assertGET();
        RestListProcessorsResponse.Builder response = RestListProcessorsResponse.newBuilder();
        for (YProcessor processor : YProcessor.getChannels()) {
            response.addProcessor(ManagementService.getProcessorInfo(processor));
        }
        return new RestResponse(req, response.build(), SchemaRest.RestListProcessorsResponse.WRITE);
    }
        
    private RestResponse handleProcessorRequest(RestRequest req, YProcessor yproc) throws RestException {
        req.assertPOST();
        ProcessorRequest yprocReq = req.readMessage(SchemaYamcsManagement.ProcessorRequest.MERGE).build();
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
        default:
            throw new BadRequestException("Invalid operation "+yprocReq.getOperation()+" specified");
        }
        RestProcessorResponse response = RestProcessorResponse.newBuilder().build();
        return new RestResponse(req, response, SchemaRest.RestProcessorResponse.WRITE);
    }
    
    private RestResponse handleProcessorManagementRequest(RestRequest req) throws RestException {
        req.assertPOST();
        ProcessorManagementRequest yprocReq = req.readMessage(SchemaYamcsManagement.ProcessorManagementRequest.MERGE).build();
        switch(yprocReq.getOperation()) {
        case CONNECT_TO_PROCESSOR:
            ManagementService mservice = ManagementService.getInstance();
            try {
                mservice.connectToProcessor(yprocReq, req.authToken);
                RestConnectToProcessorResponse response = RestConnectToProcessorResponse.newBuilder().build();
                return new RestResponse(req, response, SchemaRest.RestConnectToProcessorResponse.WRITE);
            } catch (YamcsException e) {
                throw new BadRequestException(e.getMessage());
            }
        
        case CREATE_PROCESSOR:
            mservice = ManagementService.getInstance();
            try {
                mservice.createProcessor(yprocReq, req.authToken);
                RestCreateProcessorResponse response = RestCreateProcessorResponse.newBuilder().build();
                return new RestResponse(req, response, SchemaRest.RestCreateProcessorResponse.WRITE);
            } catch (YamcsException e) {
                throw new BadRequestException(e.getMessage());
            }
        
        default:
            throw new BadRequestException("Invalid operation "+yprocReq.getOperation()+" specified");
        }
    }
}
