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
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;

import io.netty.channel.ChannelFuture;

/**
 * Gives information on clients (aka sessions)
 */
public class ClientRestHandler extends RestHandler {
    
    @Route(path="/api/clients", method="GET")
    public ChannelFuture listClients(RestRequest req) throws HttpException {
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
        }
        return sendOK(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }
    
    @Route(path="/api/clients/:id", method={ "PATCH", "PUT", "POST" })
    public ChannelFuture patchClient(RestRequest req) throws HttpException {
        ClientInfo ci = verifyClient(req, req.getIntegerRouteParam("id"));
        
        EditClientRequest request = req.bodyAsMessage(SchemaRest.EditClientRequest.MERGE).build();
        String newProcessorName = null;
        if (request.hasProcessor()) newProcessorName = request.getProcessor();
        if (req.hasQueryParameter("processor")) newProcessorName = req.getQueryParameter("processor");
        
        if (newProcessorName != null) {
            String instance = ci.getInstance(); // Only allow changes within same instance
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
                    return sendOK(req);
                } catch (YamcsException e) {
                    throw new BadRequestException(e.getMessage());
                }
            }
        }
        
        return sendOK(req);
    }
}
