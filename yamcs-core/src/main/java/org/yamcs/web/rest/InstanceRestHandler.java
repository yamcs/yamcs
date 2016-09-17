package org.yamcs.web.rest;

import java.util.Set;

import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.Rest.ListInstancesResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;
import org.yamcs.web.HttpException;

/**
 * Handles incoming requests related to yamcs instances.
 */
public class InstanceRestHandler extends RestHandler {

    @Route(path="/api/instances", method="GET")
    public void listInstances(RestRequest req) throws HttpException {
        YamcsInstances instances = YamcsServer.getYamcsInstances();
        
        ListInstancesResponse.Builder instancesb = ListInstancesResponse.newBuilder();
        for (YamcsInstance yamcsInstance : instances.getInstanceList()) {
            YamcsInstance enriched = YamcsToGpbAssembler.enrichYamcsInstance(req, yamcsInstance);
            instancesb.addInstance(enriched);
        }
        
        completeOK(req, instancesb.build(), SchemaRest.ListInstancesResponse.WRITE);
    }
    
    @Route(path="/api/instances/:instance", method="GET")
    public void getInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YamcsInstance yamcsInstance = YamcsServer.getYamcsInstance(instance);
        YamcsInstance enriched = YamcsToGpbAssembler.enrichYamcsInstance(req, yamcsInstance);
        completeOK(req, enriched, SchemaYamcsManagement.YamcsInstance.WRITE);
    }
    
    @Route(path="/api/instances/:instance/clients", method="GET")
    public void listClientsForInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            if (instance.equals(client.getInstance())) {
                responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
            }
        }
        completeOK(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }
}
