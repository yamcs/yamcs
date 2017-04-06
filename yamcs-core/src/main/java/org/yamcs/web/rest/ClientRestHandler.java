package org.yamcs.web.rest;

import java.util.Set;

import org.yamcs.Processor;
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
        String newInstance = ci.getInstance(); // By default, use same instance
        if (request.hasInstance()) {
            newInstance = request.getInstance();
        }
        if (request.hasProcessor()) {
            newProcessorName = request.getProcessor();
        }
        if (req.hasQueryParameter("processor")) {
            newProcessorName = req.getQueryParameter("processor");
        }
        if (req.hasQueryParameter("instance")) {
            newInstance = req.getQueryParameter("instance");
        }

        if (newProcessorName != null) {
            Processor newProcessor = Processor.getInstance(newInstance, newProcessorName);
            if (newProcessor == null) {
                throw new BadRequestException("Cannot switch user to non-existing processor '" + newProcessorName + "' (instance: '" + newInstance + "')");
            } else {
                ManagementService mservice = ManagementService.getInstance();
                ProcessorManagementRequest.Builder yprocReq = ProcessorManagementRequest.newBuilder();
                yprocReq.setInstance(newInstance);
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
