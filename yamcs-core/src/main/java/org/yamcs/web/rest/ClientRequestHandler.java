package org.yamcs.web.rest;

import java.util.Set;

import org.yamcs.YProcessor;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;

/**
 * Gives information on clients (aka sessions)
 */
public class ClientRequestHandler extends RestRequestHandler {
    
    @Override
    public String getPath() {
        return "clients";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        req.assertGET();
        if (req.hasPathSegment(pathOffset)) {
            String instance = req.getPathSegment(pathOffset);
            if (!YamcsServer.hasInstance(instance)) {
                throw new NotFoundException(req);
            }
            
            if (req.hasPathSegment(pathOffset + 1)) {
                String processorName = req.getPathSegment(pathOffset + 1);
                YProcessor processor = YProcessor.getInstance(instance, processorName);
                
                if (req.hasPathSegment(pathOffset + 2)) {
                    throw new NotFoundException(req);
                } else {
                    return listClientsForProcessor(req, processor);
                }
            } else {
                return listClientsForInstance(req, instance);
            }
        } else {
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
    
    private RestResponse listClientsForInstance(RestRequest req, String instance) throws RestException {
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            if (instance.equals(client.getInstance())) {
                responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
            }
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }
    
    private RestResponse listClientsForProcessor(RestRequest req, YProcessor processor) throws RestException {
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            if (processor.getInstance().equals(client.getInstance())
                    && processor.getName().equals(client.getProcessorName())) {
                responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
            }
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }
}
