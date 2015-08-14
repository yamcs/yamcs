package org.yamcs.web.rest;

import java.util.Set;

import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.RestConnectToProcessorResponse;
import org.yamcs.protobuf.Rest.RestCreateProcessorResponse;
import org.yamcs.protobuf.Rest.RestListClientsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
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
            req.assertPOST();
            return handleProcessorManagementRequest(req);
        } else if ("clients".equals(req.getPathSegment(pathOffset))) {
            req.assertGET();
            return listClients(req);
        } else {
            throw new NotFoundException(req);
        }
    }
        
    private RestResponse handleProcessorManagementRequest(RestRequest req) throws RestException {
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
    
    private RestResponse listClients(RestRequest req) throws RestException {
        Set<ClientInfo> clients = ManagementService.getInstance().getAllClientInfo();
        RestListClientsResponse.Builder responseb = RestListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            responseb.addClientInfo(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
        }
        return new RestResponse(req, responseb.build(), SchemaRest.RestListClientsResponse.WRITE);
    }
}
