package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Rest.ListInstancesResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to yamcs instances.
 */
public class InstanceRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(InstanceRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "instances";
    }
    
    @Override
    protected RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            return listInstances(req);
        } else {
            String instance = req.getPathSegment(pathOffset);
            if (!YamcsServer.hasInstance(instance)) {
                throw new NotFoundException(req);
            } else {
                YamcsInstance yamcsInstance = YamcsServer.getYamcsInstance(instance);
                return getInstance(req, yamcsInstance);
            }
        }
    }

    /**
     * Lists all instances
     */
    private RestResponse listInstances(RestRequest req) throws RestException {
        YamcsInstances instances = YamcsServer.getYamcsInstances();
        
        ListInstancesResponse.Builder instancesb = ListInstancesResponse.newBuilder();
        for (YamcsInstance yamcsInstance : instances.getInstanceList()) {
            instancesb.addInstance(enrichYamcsInstance(req, yamcsInstance));
        }
        
        return new RestResponse(req, instancesb.build(), SchemaRest.ListInstancesResponse.WRITE);
    }
    
    /**
     * Get a single instance
     */
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
        
        String apiUrl = req.getApiURL();            
        instanceb.setUrl(apiUrl + "/instances/" + instanceb.getName());
        instanceb.setEventsUrl(apiUrl + "/events/" + instanceb.getName());
        instanceb.setClientsUrl(apiUrl + "/clients/" + instanceb.getName() + "{/processor}");
        
        for (YProcessor processor : YProcessor.getChannels(instanceb.getName())) {
            instanceb.addProcessor(ProcessorRequestHandler.toProcessorInfo(processor, req, false));
        }
        return instanceb.build();
    }
}
