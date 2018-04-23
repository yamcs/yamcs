package org.yamcs.web.rest;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.Rest.ListInstancesResponse;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;
import org.yamcs.security.Privilege;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.ExceptionUtil;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;

/**
 * Handles incoming requests related to yamcs instances.
 */
public class InstanceRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);

    @Route(path = "/api/instances", method = "GET")
    public void listInstances(RestRequest req) throws HttpException {
        YamcsInstances instances = YamcsServer.getYamcsInstances();

        ListInstancesResponse.Builder instancesb = ListInstancesResponse.newBuilder();
        for (YamcsInstance yamcsInstance : instances.getInstanceList()) {
            YamcsInstance enriched = YamcsToGpbAssembler.enrichYamcsInstance(req, yamcsInstance);
            instancesb.addInstance(enriched);
        }

        completeOK(req, instancesb.build());
    }

    @Route(path = "/api/instances/:instance", method = "GET")
    public void getInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YamcsInstance yamcsInstance = YamcsServer.getYamcsInstance(instance);
        YamcsInstance enriched = YamcsToGpbAssembler.enrichYamcsInstance(req, yamcsInstance);
        completeOK(req, enriched);
    }

    @Route(path = "/api/instances/:instance/clients", method = "GET")
    public void listClientsForInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            if (instance.equals(client.getInstance())) {
                responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
            }
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/instances/:instance", method = { "PATCH", "PUT", "POST" })
    public void editInstance(RestRequest req) throws HttpException {
        if (!Privilege.getInstance().hasPrivilege1(req.getAuthToken(), SystemPrivilege.MayControlServices)) {
            throw new ForbiddenException("No privilege for this operation");
        }
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        String state;
        if (req.hasQueryParameter("state")) {
            state = req.getQueryParameter("state");
        } else {
            throw new BadRequestException("No state specified");
        }

        ManagementService mgr = ManagementService.getInstance();
        CompletableFuture<Void> cf;
        switch (state.toLowerCase()) {
        case "stop":
        case "stopped":
            cf = mgr.stopInstance(instance);
            break;
        case "restarted":
            cf = mgr.restartInstance(instance);
            break;
        default:
            throw new BadRequestException("Unsupported service state '" + state + "'");
        }
        cf.whenComplete((v, error) -> {
            if (error == null) {
                completeOK(req);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                log.error("Error when changing instance state to {}", state, t);
                completeWithError(req, new InternalServerErrorException(t));
            }
        });
    }

}
