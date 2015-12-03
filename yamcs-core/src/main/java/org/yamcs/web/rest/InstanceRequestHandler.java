package org.yamcs.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
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
import org.yamcs.web.rest.RestRequest.Option;
import org.yamcs.web.rest.mdb.MDBRequestHandler;
import org.yamcs.web.rest.processor.ProcessorRequestHandler;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to yamcs instances.
 */
public class InstanceRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(InstanceRequestHandler.class.getName());
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            return listInstances(req);
        } else {
            String instance = req.getPathSegment(pathOffset);
            if (!YamcsServer.hasInstance(instance)) {
                throw new NotFoundException(req);
            }
            YamcsInstance yamcsInstance = YamcsServer.getYamcsInstance(instance);
            if (!req.hasPathSegment(pathOffset + 1)) {
                req.assertGET();
                return getInstance(req, yamcsInstance);
            } else {
                String resource = req.getPathSegment(pathOffset + 1);
                switch (resource) {
                case "clients":
                    req.assertGET();
                    return listClientsForInstance(req, instance);
                default:
                    throw new NotFoundException(req, "No resource '" + resource + "' for instance '" + instance +"'");
                }
            }
        }
    }

    private RestResponse listInstances(RestRequest req) throws RestException {
        YamcsInstances instances = YamcsServer.getYamcsInstances();
        
        ListInstancesResponse.Builder instancesb = ListInstancesResponse.newBuilder();
        for (YamcsInstance yamcsInstance : instances.getInstanceList()) {
            instancesb.addInstance(enrichYamcsInstance(req, yamcsInstance));
        }
        
        return new RestResponse(req, instancesb.build(), SchemaRest.ListInstancesResponse.WRITE);
    }
    
    private RestResponse getInstance(RestRequest req, YamcsInstance yamcsInstance) throws RestException {
        YamcsInstance enriched = enrichYamcsInstance(req, yamcsInstance);
        return new RestResponse(req, enriched, SchemaYamcsManagement.YamcsInstance.WRITE);
    }
    
    private YamcsInstance enrichYamcsInstance(RestRequest req, YamcsInstance yamcsInstance) {
        YamcsInstance.Builder instanceb = YamcsInstance.newBuilder(yamcsInstance);
        
        // Override MDB with a version that has URLs too
        if (yamcsInstance.hasMissionDatabase()) {
            XtceDb mdb = XtceDbFactory.getInstance(yamcsInstance.getName());
            instanceb.setMissionDatabase(MDBRequestHandler.toMissionDatabase(req, yamcsInstance.getName(), mdb)); 
        }
        
        if (!req.getOptions().contains(Option.NO_LINK)) {
            String apiUrl = req.getApiURL();
            String instanceUrl = apiUrl + "/instances/" + instanceb.getName();
            instanceb.setUrl(instanceUrl);
            instanceb.setEventsUrl(instanceUrl + "{/processor}/events");
            instanceb.setClientsUrl(instanceUrl + "{/processor}/clients");
        }
        
        for (YProcessor processor : YProcessor.getChannels(instanceb.getName())) {
            instanceb.addProcessor(ProcessorRequestHandler.toProcessorInfo(processor, req, false));
        }
        return instanceb.build();
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
}
