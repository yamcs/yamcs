package org.yamcs.web.rest;

import java.util.Set;

import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;

/**
 * Gives information on clients (aka sessions)
 */
public class ClientsRequestHandler extends RestRequestHandler {
    
    @Override
    public String getPath() {
        return "clients";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        req.assertGET();
        return listClients(req);
    }
           
    
    private RestResponse listClients(RestRequest req) throws RestException {
        Set<ClientInfo> clients = ManagementService.getInstance().getAllClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            if (req.getYamcsInstance() == null || req.getYamcsInstance().equals(client.getInstance())) {
                responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
            }
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }
}
