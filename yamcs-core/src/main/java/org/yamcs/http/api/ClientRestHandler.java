package org.yamcs.http.api;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.ClientInfo;
import org.yamcs.protobuf.ClientInfo.ClientState;
import org.yamcs.protobuf.EditClientRequest;
import org.yamcs.protobuf.ListClientsResponse;
import org.yamcs.protobuf.ProcessorManagementRequest;
import org.yamcs.protobuf.ProcessorManagementRequest.Operation;
import org.yamcs.protobuf.YamcsInstance.InstanceState;
import org.yamcs.security.SystemPrivilege;

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

    @Route(path = "/api/instances/{instance}/clients", method = "GET")
    public void listClientsForInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req.getRouteParam("instance"));
        Set<ConnectedClient> clients = ManagementService.getInstance().getClients();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ConnectedClient client : clients) {
            if (client.getProcessor() != null && instance.equals(client.getProcessor().getInstance())) {
                responseb.addClient(YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED));
            }
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/clients/{id}", method = "GET")
    public void getClient(RestRequest req) throws HttpException {
        ConnectedClient client = verifyClient(req, req.getIntegerRouteParam("id"));
        ClientInfo clientInfo = YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED);
        completeOK(req, clientInfo);
    }

    @Route(path = "/api/clients/{id}", method = "PATCH")
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

            YamcsServerInstance ysi = YamcsServer.getServer().getInstance(newInstance);
            if (ysi == null) {
                throw new BadRequestException(String.format("Cannot join unknown instance '" + newInstance + "'"));
            } else if (ysi.state() == InstanceState.OFFLINE) {
                throw new BadRequestException("Cannot join an offline instance");
            } else if (newProcessor == null) {
                if (request.hasProcessor()) {
                    throw new BadRequestException(String.format("Cannot switch user to non-existing processor %s/%s",
                            newInstance, request.getProcessor()));
                } else {
                    // TODO we should allow this...
                    throw new BadRequestException(String.format("No processor for instance '" + newInstance + "'"));
                }
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
        if (hasSystemPrivilege(req.getUser(), SystemPrivilege.ControlProcessor)) {
            // With this privilege, everything is allowed
            return;
        }

        String username = req.getUser().getName();

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
        if (!client.getUser().getName().equals(username)) {
            log.warn("User {} is not allowed to connect {} to new processor", username, client.getUser());
            throw new ForbiddenException("Not allowed to connect other client than your own");
        }
    }
}
