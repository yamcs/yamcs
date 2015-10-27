package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.ListInstancesResponse;
import org.yamcs.protobuf.Yamcs.YamcsInstance;
import org.yamcs.protobuf.Yamcs.YamcsInstances;
import org.yamcs.web.HttpSocketServer;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * Handles incoming requests related to yamcs instances.
 */
public class InstancesRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(InstancesRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "instances";
    }
    
    @Override
    protected RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        String seg = req.getPathSegment(pathOffset - 1);
        if (getPath().equals(seg)) {
            if (!req.hasPathSegment(pathOffset)) {
                req.assertGET();
                return getInstances(req);    
            }
        } else {
            if (!req.hasPathSegment(pathOffset)) {
                for (YamcsInstance yamcsInstance : YamcsServer.getYamcsInstances().getInstanceList()) {
                    if (yamcsInstance.getName().equals(seg)) {
                        return getInstance(req, yamcsInstance);
                    }
                }
            }
        }
        
        throw new NotFoundException(req);
    }

    /**
     * Lists all instances
     * <p>
     * GET /instances
     */
    private RestResponse getInstances(RestRequest req) throws RestException {
        YamcsInstances instances = YamcsServer.getYamcsInstances();
        
        ListInstancesResponse.Builder instancesb = ListInstancesResponse.newBuilder();
        for (YamcsInstance yamcsInstance : instances.getInstanceList()) {
            YamcsInstance.Builder instanceb = YamcsInstance.newBuilder(yamcsInstance);
            
            if (HttpSocketServer.getInstance().isInstanceRegistered(instanceb.getName())) {
                String instanceURL = req.getBaseURL() + "/api/" + instanceb.getName();
                instanceb.setUrl(instanceURL);
                instanceb.setAlarmsUrl(instanceURL + "/alarms");
                instanceb.setParametersUrl(instanceURL + "/parameters");
            }
            instancesb.addInstance(instanceb);
        }
        
        return new RestResponse(req, instancesb.build(), SchemaYamcs.ListInstancesResponse.WRITE);
    }
    
    /**
     * Get a single instance
     * <p>
     * GET /:instance
     */
    private RestResponse getInstance(RestRequest req, YamcsInstance yamcsInstance) throws RestException {
        YamcsInstance.Builder instanceb = YamcsInstance.newBuilder(yamcsInstance);
        
        if (HttpSocketServer.getInstance().isInstanceRegistered(instanceb.getName())) {
            String scheme = (req.isSSL()) ? "https://" : "http://";
            String host = req.getHeader(HttpHeaders.Names.HOST);
            String baseUrl = (host != null) ? scheme + host + "/api/" : "/api/";
            
            instanceb.setUrl(baseUrl + instanceb.getName());
            instanceb.setAlarmsUrl(baseUrl + instanceb.getName() + "/alarms");
            instanceb.setParametersUrl(baseUrl + instanceb.getName() + "/parameters");
        }
        
        return new RestResponse(req, instanceb.build(), SchemaYamcs.YamcsInstance.WRITE);
    }
}
