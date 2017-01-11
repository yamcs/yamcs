package org.yamcs.web.rest.mdb;

import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Rest.ListContainerInfoResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to container info from the MDB
 */
public class MDBContainerRestHandler extends RestHandler {
    
    @Route(path = "/api/mdb/:instance/containers", method = "GET")
    @Route(path = "/api/mdb/:instance/containers/:name*", method = "GET")
    public void getContainer(RestRequest req) throws HttpException {
        if (req.hasRouteParam("name")) {
            getContainerInfo(req);
        } else {
            listContainers(req);
        }
    }
    
    private void getContainerInfo(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        SequenceContainer c = verifyContainer(req, mdb, req.getRouteParam("name"));
        
        String instanceURL = req.getApiURL() + "/mdb/" + instance;
        ContainerInfo cinfo = XtceToGpbAssembler.toContainerInfo(c, instanceURL, DetailLevel.FULL, req.getOptions());
        completeOK(req, cinfo, SchemaMdb.ContainerInfo.WRITE);
    }
    
    private void listContainers(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        
        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        String instanceURL = req.getApiURL() + "/mdb/" + instance;
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        
        ListContainerInfoResponse.Builder responseb = ListContainerInfoResponse.newBuilder();
        if (req.hasQueryParameter("namespace")) {
            String namespace = req.getQueryParameter("namespace");
            
            for (SequenceContainer c : mdb.getSequenceContainers()) {
                if (matcher != null && !matcher.matches(c))
                    continue;
                
                String alias = c.getAlias(namespace);
                if (alias != null || (recurse && c.getQualifiedName().startsWith(namespace))) {
                    responseb.addContainer(XtceToGpbAssembler.toContainerInfo(c, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
                }
            }
        } else { // List all
            for (SequenceContainer c : mdb.getSequenceContainers()) {
                if (matcher != null && !matcher.matches(c)) continue;
                responseb.addContainer(XtceToGpbAssembler.toContainerInfo(c, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
            }
        }
        
        completeOK(req, responseb.build(), SchemaRest.ListContainerInfoResponse.WRITE);
    }
}
