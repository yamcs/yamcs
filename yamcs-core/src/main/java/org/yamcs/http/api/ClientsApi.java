package org.yamcs.http.api;

import java.util.Set;

import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.NotFoundException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.AbstractClientsApi;
import org.yamcs.protobuf.ClientInfo;
import org.yamcs.protobuf.ClientInfo.ClientState;
import org.yamcs.protobuf.EditClientRequest;
import org.yamcs.protobuf.GetClientRequest;
import org.yamcs.protobuf.ListClientsResponse;
import org.yamcs.protobuf.ProcessorManagementRequest;
import org.yamcs.protobuf.ProcessorManagementRequest.Operation;
import org.yamcs.protobuf.YamcsInstance.InstanceState;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Empty;

public class ClientsApi extends AbstractClientsApi<Context> {

    @Override
    public void listClients(Context ctx, Empty request, Observer<ListClientsResponse> observer) {
        Set<ConnectedClient> clients = ManagementService.getInstance().getClients();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ConnectedClient client : clients) {
            ClientInfo clientInfo = toClientInfo(client, ClientState.CONNECTED);
            responseb.addClients(clientInfo);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getClient(Context ctx, GetClientRequest request, Observer<ClientInfo> observer) {
        ConnectedClient client = verifyClient(request.getId());
        ClientInfo clientInfo = toClientInfo(client, ClientState.CONNECTED);
        observer.complete(clientInfo);
    }

    @Override
    public void updateClient(Context ctx, EditClientRequest request, Observer<Empty> observer) {
        ConnectedClient client = verifyClient(request.getId());

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
            verifyPermission(ctx, newProcessor, client.getId());

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

        observer.complete(Empty.getDefaultInstance());
    }

    private static ConnectedClient verifyClient(int clientId) throws NotFoundException {
        ConnectedClient client = ManagementService.getInstance().getClient(clientId);
        if (client == null) {
            throw new NotFoundException("No such client");
        } else {
            return client;
        }
    }

    private void verifyPermission(Context ctx, Processor processor, int clientId)
            throws HttpException {
        if (ctx.user.hasSystemPrivilege(SystemPrivilege.ControlProcessor)) {
            // With this privilege, everything is allowed
            return;
        }

        // other users can only connect clients to the processor they own
        if (!(processor.isPersistent() || processor.getCreator().equals(ctx.user.getName()))) {
            throw new ForbiddenException("not allowed to connect clients other than yours");
        }

        // and finally they can only connect their own clients
        ConnectedClient client = ManagementService.getInstance().getClient(clientId);
        if (client == null) {
            throw new BadRequestException("Invalid client id " + clientId);
        }
        if (!client.getUser().getName().equals(ctx.user.getName())) {
            throw new ForbiddenException("Not allowed to connect other client than your own");
        }
    }

    public static ClientInfo toClientInfo(ConnectedClient client, ClientState state) {
        ClientInfo.Builder clientb = ClientInfo.newBuilder()
                .setApplicationName(client.getApplicationName())
                .setAddress(client.getAddress())
                .setUsername(client.getUser().getName())
                .setId(client.getId())
                .setState(state)
                .setLoginTime(TimeEncoding.toProtobufTimestamp(client.getLoginTime()));

        Processor processor = client.getProcessor();
        if (processor != null) {
            clientb.setInstance(processor.getInstance());
            clientb.setProcessorName(processor.getName());
        }
        return clientb.build();
    }
}
