package org.yamcs.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.EditClientRequest;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest.Operation;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;

/**
 * Gives information on clients (aka sessions)
 */
public class ClientRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(ClientRestHandler.class);

    @Route(path = "/api/clients", method = "GET")
    public void listClients(RestRequest req) throws HttpException {
        Set<ConnectedClient> clients = ManagementService.getInstance().getClients();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ConnectedClient client : clients) {
            ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED);
            responseb.addClient(clientInfo);
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/clients/:id", method = "GET")
    public void getClient(RestRequest req) throws HttpException {
        ConnectedClient client = verifyClient(req, req.getIntegerRouteParam("id"));
        ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED);
        completeOK(req, clientInfo);
    }

    @Route(path = "/api/clients/:id", method = { "PATCH", "PUT", "POST" })
    public void patchClient(RestRequest restReq) throws HttpException {
        ConnectedClient client = verifyClient(restReq, restReq.getIntegerRouteParam("id"));

        EditClientRequest request = restReq.bodyAsMessage(EditClientRequest.newBuilder()).build();

        if (request.hasInstance() || request.hasProcessor()) {
            String newInstance;
            Processor newProcessor;
            if (request.hasProcessor()) {
                newInstance = (request.hasInstance()) ? request.getInstance() : client.getProcessor().getInstance();
                newProcessor = Processor.getInstance(newInstance, request.getProcessor());
            } else { // Switch to default processor of the instance
                newInstance = request.getInstance();
                newProcessor = Processor.getFirstProcessor(request.getInstance());
            }

            if (newProcessor == null) {
                throw new BadRequestException(String.format("Cannot switch user to non-existing processor %s/%s",
                        newInstance, request.getProcessor()));
            }
            verifyPermission(newProcessor, client.getId(), restReq);

            ManagementService mservice = ManagementService.getInstance();
            ProcessorManagementRequest.Builder procReq = ProcessorManagementRequest.newBuilder();
            procReq.setInstance(newInstance);
            procReq.setName(newProcessor.getName());
            procReq.setOperation(Operation.CONNECT_TO_PROCESSOR);
            procReq.addClientId(client.getId());
            try {
                mservice.connectToProcessor(procReq.build());
            } catch (YamcsException e) {
                throw new BadRequestException(e.getMessage());
            }
        }

        completeOK(restReq);
    }

    private void verifyPermission(Processor processor, int clientId, RestRequest req)
            throws HttpException {
        if (hasSystemPrivilege(req, SystemPrivilege.ControlProcessor)) {
            // With this privilege, everything is allowed
            return;
        }

        String username = req.getUser().getUsername();

        // other users can only connect clients to the processor they own
        if (!(processor.isPersistent() || processor.getCreator().equals(username))) {
            log.warn("User {} is not allowed to connect users to processor {}", username, processor.getName());
            throw new ForbiddenException("not allowed to connect clients other than yours");
        }

        // and finally they can only connect their own clients
        ConnectedClient client = ManagementService.getInstance().getClient(clientId);
        if (client == null) {
            throw new BadRequestException("Invalid client id " + clientId);
        }
        if (!client.getUser().getUsername().equals(username)) {
            log.warn("User {} is not allowed to connect {} to new processor", username, client.getUser());
            throw new ForbiddenException("Not allowed to connect other client than your own");
        }
    }
}
