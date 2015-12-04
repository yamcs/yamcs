package org.yamcs.web.rest;

import java.util.Set;

import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.EditClientRequest;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest.Operation;

/**
 * Gives information on clients (aka sessions)
 */
public class ClientRequestHandler extends RestRequestHandler {
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (req.hasPathSegment(pathOffset)) {
            int clientId = Integer.parseInt(req.getPathSegment(pathOffset));
            ClientInfo ci = ManagementService.getInstance().getClientInfo(clientId);
            if (ci == null) {
                throw new NotFoundException(req, "No such client");
            } else {
                if (req.isPATCH() || req.isPOST() || req.isPUT()) {
                    return patchClient(req, ci);
                } else {
                    throw new MethodNotAllowedException(req);
                }
            }
        } else {
            req.assertGET();
            return listClients(req);
        }
    }
    
    private RestResponse listClients(RestRequest req) throws RestException {
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }
    
    private RestResponse patchClient(RestRequest req, ClientInfo ci) throws RestException {
        EditClientRequest request = req.bodyAsMessage(SchemaRest.EditClientRequest.MERGE).build();
        String processor = null;
        if (request.hasProcessor()) processor = request.getProcessor();
        if (req.hasQueryParameter("processor")) processor = req.getQueryParameter("processor");
        
        if (processor != null) {
            String instance = ci.getInstance(); // Only allow changes within same instance
            YProcessor yproc = YProcessor.getInstance(instance, processor);
            if (yproc == null) {
                throw new NotFoundException(req, "No processor named '" + processor + "' for instance '" + instance + "'");
            } else {
                ManagementService mservice = ManagementService.getInstance();
                ProcessorManagementRequest.Builder yprocReq = ProcessorManagementRequest.newBuilder();
                yprocReq.setInstance(instance);
                yprocReq.setName(processor);
                yprocReq.setOperation(Operation.CONNECT_TO_PROCESSOR);
                yprocReq.addClientId(ci.getId());
                try {
                    mservice.connectToProcessor(yprocReq.build(), req.getAuthToken());
                    return new RestResponse(req);
                } catch (YamcsException e) {
                    throw new BadRequestException(e.getMessage());
                }
            }
        }
        
        return new RestResponse(req);
    }
}
