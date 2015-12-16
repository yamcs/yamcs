package org.yamcs.web.rest.mdb;

import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Rest.ListAlgorithmInfoResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.channel.ChannelFuture;

/**
 * Handles incoming requests related to algorithm info from the MDB
 */
public class MDBAlgorithmRestHandler extends RestHandler {
    
    @Route(path = "/api/mdb/:instance/algorithms/:name*", method = "GET")
    public ChannelFuture getAlgorithm(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Algorithm algorithm = verifyAlgorithm(req, mdb, req.getRouteParam("name"));
        
        String instanceURL = req.getApiURL() + "/mdb/" + instance;
        AlgorithmInfo ainfo = XtceToGpbAssembler.toAlgorithmInfo(algorithm, instanceURL, DetailLevel.FULL, req.getOptions());
        return sendOK(req, ainfo, SchemaMdb.AlgorithmInfo.WRITE);
    }

    /**
     * Sends the algorithms for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    @Route(path = "/api/mdb/:instance/algorithms", method = "GET")
    public ChannelFuture listAlgorithms(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        
        String instanceURL = req.getApiURL() + "/mdb/" + instance;
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListAlgorithmInfoResponse.Builder responseb = ListAlgorithmInfoResponse.newBuilder();
        //if (namespace == null) {
            for (Algorithm a : mdb.getAlgorithms()) {
                if (matcher != null && !matcher.matches(a)) continue;
                responseb.addAlgorithm(XtceToGpbAssembler.toAlgorithmInfo(a, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
            }
        /*} else {
            // TODO privileges
            for (Algorithm a : mdb.getAlgorithms()) {
                if (matcher != null && !matcher.matches(a))
                    continue;
                
                String alias = a.getAlias(namespace);
                if (alias != null || (recurse && a.getQualifiedName().startsWith(namespace))) {
                    responseb.addAlgorithm(XtceToGpbAssembler.toAlgorithmInfo(a, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
                }
            }
        }*/
        
        return sendOK(req, responseb.build(), SchemaRest.ListAlgorithmInfoResponse.WRITE);
    }
}
