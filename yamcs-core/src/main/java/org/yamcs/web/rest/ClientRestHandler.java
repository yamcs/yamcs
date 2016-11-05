package org.yamcs.web.rest;

import java.util.Set;

import org.yamcs.YConfiguration;
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
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;

/**
 * Gives information on clients (aka sessions)
 */
public class ClientRestHandler extends RestHandler {
    //According to the docs, it is not possible to changes instances once connected. This is for good reason - a different instance will have a different MDB, 
    // parameter subscriptions may become invalid and other inconsistencies may happen.
    
    //Still it was possible in the past and some people are used to it because the CORBA clients do not allow easily to specify the instance when connecting.
    boolean allowChangingInstances;
    
    public ClientRestHandler() {
        YConfiguration yconfig = YConfiguration.getConfiguration("yamcs");
        allowChangingInstances = yconfig.getBoolean("allowChangingInstances", false);
    }
    
    @Route(path="/api/clients", method="GET")
    public void listClients(RestRequest req) throws HttpException {
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
        }
        completeOK(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }
    
    @Route(path="/api/clients/:id", method={ "PATCH", "PUT", "POST" })
    public void patchClient(RestRequest req) throws HttpException {
        ClientInfo ci = verifyClient(req, req.getIntegerRouteParam("id"));
        
        EditClientRequest request = req.bodyAsMessage(SchemaRest.EditClientRequest.MERGE).build();
        String newProcessorName = null;
        String instance = ci.getInstance();// Only allow changes within same instance
        if (request.hasProcessor()) newProcessorName = request.getProcessor();
        if (req.hasQueryParameter("processor")) newProcessorName = req.getQueryParameter("processor");
        if (req.hasQueryParameter("instance")) {
            if(allowChangingInstances) {
                instance = req.getQueryParameter("instance");
            } else {
                String newInst = req.getQueryParameter("instance");
                if(!instance.equals(newInst)) {
                    throw new BadRequestException("Changing instances is not allowed unless allowChangingInstances is set to true in the yamcs.yaml config file.");
                }
            }
        }
        
        if (newProcessorName != null) {            
            YProcessor newProcessor = YProcessor.getInstance(instance, newProcessorName);
            if (newProcessor == null) {
                throw new BadRequestException("Cannot switch user to non-existing processor '" + newProcessorName + "' (instance: '" + instance + "')");
            } else {
                ManagementService mservice = ManagementService.getInstance();
                ProcessorManagementRequest.Builder yprocReq = ProcessorManagementRequest.newBuilder();
                yprocReq.setInstance(instance);
                yprocReq.setName(newProcessorName);
                yprocReq.setOperation(Operation.CONNECT_TO_PROCESSOR);
                yprocReq.addClientId(ci.getId());
                try {
                    mservice.connectToProcessor(yprocReq.build(), req.getAuthToken());
                    completeOK(req);
                    return;
                } catch (YamcsException e) {
                    throw new BadRequestException(e.getMessage());
                }
            }
        }
        
        completeOK(req);
    }
}
