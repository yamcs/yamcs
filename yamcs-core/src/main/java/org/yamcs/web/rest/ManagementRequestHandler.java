package org.yamcs.web.rest;

import java.util.Set;

import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ListClientsResponse;

/**
 * /(instance)/management
 */
public class ManagementRequestHandler implements RestRequestHandler {
    
    @Override
    public String getPath() {
        return "management";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
       if ("clients".equals(req.getPathSegment(pathOffset))) {
            req.assertGET();
            return listClients(req);
        } else {
            throw new NotFoundException(req);
        }
    }
           
    
    private RestResponse listClients(RestRequest req) throws RestException {
        Set<ClientInfo> clients = ManagementService.getInstance().getAllClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            responseb.addClientInfo(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
        }
        return new RestResponse(req, responseb.build(), SchemaYamcsManagement.ListClientsResponse.WRITE);
    }
}
