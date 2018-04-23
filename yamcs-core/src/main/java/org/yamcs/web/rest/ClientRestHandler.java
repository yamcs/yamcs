package org.yamcs.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.YamcsException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.EditClientRequest;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest.Operation;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
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
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/clients/:id", method = "GET")
    public void getClient(RestRequest req) throws HttpException {
        ClientInfo ci = verifyClient(req, req.getIntegerRouteParam("id"));
        ClientInfo.Builder responseb = ClientInfo.newBuilder(ci).setState(ClientState.CONNECTED);
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/clients/:id", method = { "PATCH", "PUT", "POST" })
    public void patchClient(RestRequest restReq) throws HttpException {
        ClientInfo ci = verifyClient(restReq, restReq.getIntegerRouteParam("id"));

        EditClientRequest request = restReq.bodyAsMessage(EditClientRequest.newBuilder()).build();
        String newProcessorName = null;
        String newInstance = ci.getInstance(); // By default, use same instance
        if (request.hasInstance()) {
            newInstance = request.getInstance();
        }
        if (request.hasProcessor()) {
            newProcessorName = request.getProcessor();
        }
        if (restReq.hasQueryParameter("processor")) {
            newProcessorName = restReq.getQueryParameter("processor");
        }
        if (restReq.hasQueryParameter("instance")) {
            newInstance = restReq.getQueryParameter("instance");
        }

        if (newProcessorName != null) {
            Processor newProcessor = Processor.getInstance(newInstance, newProcessorName);
            if (newProcessor == null) {
                throw new BadRequestException("Cannot switch user to non-existing processor '" + newProcessorName
                        + "' (instance: '" + newInstance + "')");
            } else {
                verifyPermission(newProcessor, ci.getId(), restReq.getAuthToken());

                ManagementService mservice = ManagementService.getInstance();
                ProcessorManagementRequest.Builder procReq = ProcessorManagementRequest.newBuilder();
                procReq.setInstance(newInstance);
                procReq.setName(newProcessorName);
                procReq.setOperation(Operation.CONNECT_TO_PROCESSOR);
                procReq.addClientId(ci.getId());
                try {
                    mservice.connectToProcessor(procReq.build());
                    completeOK(restReq);
                    return;
                } catch (YamcsException e) {
                    throw new BadRequestException(e.getMessage());
                }
            }
        }

        completeOK(restReq);
    }

    private void verifyPermission(Processor processor, int clientId, AuthenticationToken authToken)
            throws HttpException {
        String username = Privilege.getInstance().getUsername(authToken);
        if (Privilege.getInstance().hasPrivilege1(authToken, SystemPrivilege.MayControlProcessor)) {
            // MayControlProcesor can do whatever they want
            return;
        }

        // other users can only connect clients to the processor they own
        if (!(processor.isPersistent() || processor.getCreator().equals(username))) {
            log.warn("User {} is not allowed to connect users to processor {}", username, processor.getName());
            throw new ForbiddenException("not allowed to connect clients other than yours");
        }

        // and finally they can only connect their own clients
        ClientInfo ci = ManagementService.getInstance().getClientInfo(clientId);
        if (ci == null) {
            throw new BadRequestException("Invalid client id " + clientId);
        }
        if (!ci.getUsername().equals(username)) {
            log.warn("User {} is not allowed to connect {} to new processor", username, ci.getUsername());
            throw new ForbiddenException("Not allowed to connect other client than your own");
        }
    }

}
