package org.yamcs.web.rest;

import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.RestConnectToProcessorResponse;
import org.yamcs.protobuf.Rest.RestCreateProcessorResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;

/**
 * /(instance)/api/management
 */
public class ManagementRequestHandler implements RestRequestHandler {
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        if("processor".equals(req.getPathSegment(pathOffset))) {
            return handleProcessorManagementRequest(req);
        } else {
            throw new NotFoundException(req);
        }
    }
        
    private RestResponse handleProcessorManagementRequest(RestRequest req) throws RestException {
        req.assertPOST();
        ProcessorManagementRequest yprocReq = req.bodyAsMessage(SchemaYamcsManagement.ProcessorManagementRequest.MERGE).build();
        switch(yprocReq.getOperation()) {
        case CONNECT_TO_PROCESSOR:
            ManagementService mservice = ManagementService.getInstance();
            try {
                mservice.connectToProcessor(yprocReq, req.authToken);
                RestConnectToProcessorResponse response = RestConnectToProcessorResponse.newBuilder().build();
                return new RestResponse(req, response, SchemaRest.RestConnectToProcessorResponse.WRITE);
            } catch (YamcsException e) {
                throw new BadRequestException(e);
            }
        
        case CREATE_PROCESSOR:
            mservice = ManagementService.getInstance();
            try {
                mservice.createProcessor(yprocReq, req.authToken);
                RestCreateProcessorResponse response = RestCreateProcessorResponse.newBuilder().build();
                return new RestResponse(req, response, SchemaRest.RestCreateProcessorResponse.WRITE);
            } catch (YamcsException e) {
                throw new BadRequestException(e);
            }
        
        default:
            throw new BadRequestException("Invalid operation "+yprocReq.getOperation()+" specified");
        }
    }
}
