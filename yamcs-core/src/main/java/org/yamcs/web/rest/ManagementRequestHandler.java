package org.yamcs.web.rest;

import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.RestConnectToProcessorResponse;
import org.yamcs.protobuf.Rest.RestCreateProcessorResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;

public class ManagementRequestHandler extends AbstractRestRequestHandler {
    
    @Override
    public RestResponse handleRequest(RestRequest req) throws RestException {
        String[] path = req.getRemainingUri().split("/", 2);
        
        if (path.length == 0) {
            throw new NotFoundException(req);
        }
        if("processor".equals(req.qsDecoder.path())) {
            return handleProcessorManagementRequest(req);
        } else {
            throw new NotFoundException(req);
        }
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
