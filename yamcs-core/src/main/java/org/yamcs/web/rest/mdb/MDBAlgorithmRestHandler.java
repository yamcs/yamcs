package org.yamcs.web.rest.mdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Rest.ListAlgorithmInfoResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.web.HttpException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.XtceToGpbAssembler;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.web.rest.mdb.MDBHelper.MatchResult;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.XtceDb;

import io.netty.channel.ChannelFuture;

/**
 * Handles incoming requests related to algorithm info from the MDB
 */
public class MDBAlgorithmRestHandler extends RestHandler {
    final static Logger log = LoggerFactory.getLogger(MDBAlgorithmRestHandler.class.getName());
    
    @Override
    public ChannelFuture handleRequest(RestRequest req, int pathOffset) throws HttpException {
        XtceDb mdb = req.getFromContext(MDBRestHandler.CTX_MDB);
        if (!req.hasPathSegment(pathOffset)) {
            return listAlgorithms(req, null, mdb); // root namespace
        } else {
            MatchResult<Algorithm> am = MDBHelper.matchAlgorithmName(req, pathOffset);
            if (am.matches()) { // algorithm
                return getSingleAlgorithm(req, am.getRequestedId(), am.getMatch());
            } else { // namespace
                return listAlgorithmsOrError(req, pathOffset);
            }
        }
    }
    
    private ChannelFuture listAlgorithmsOrError(RestRequest req, int pathOffset) throws HttpException {
        XtceDb mdb = req.getFromContext(MDBRestHandler.CTX_MDB);
        MatchResult<String> nsm = MDBHelper.matchXtceDbNamespace(req, pathOffset, true);
        if (nsm.matches()) {
            return listAlgorithms(req, nsm.getMatch(), mdb);
        } else {
            throw new NotFoundException(req);
        }
    }
    
    private ChannelFuture getSingleAlgorithm(RestRequest req, NamedObjectId id, Algorithm a) throws HttpException {
        // TODO privileges
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        AlgorithmInfo ainfo = XtceToGpbAssembler.toAlgorithmInfo(a, instanceURL, DetailLevel.FULL, req.getOptions());
        return sendOK(req, ainfo, SchemaMdb.AlgorithmInfo.WRITE);
    }

    /**
     * Sends the containers for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private ChannelFuture listAlgorithms(RestRequest req, String namespace, XtceDb mdb) throws HttpException {
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListAlgorithmInfoResponse.Builder responseb = ListAlgorithmInfoResponse.newBuilder();
        if (namespace == null) {
            for (Algorithm a : mdb.getAlgorithms()) {
                if (matcher != null && !matcher.matches(a)) continue;
                responseb.addAlgorithm(XtceToGpbAssembler.toAlgorithmInfo(a, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
            }
        } else {
            // TODO privileges
            for (Algorithm a : mdb.getAlgorithms()) {
                if (matcher != null && !matcher.matches(a))
                    continue;
                
                String alias = a.getAlias(namespace);
                if (alias != null || (recurse && a.getQualifiedName().startsWith(namespace))) {
                    responseb.addAlgorithm(XtceToGpbAssembler.toAlgorithmInfo(a, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
                }
            }
        }
        
        return sendOK(req, responseb.build(), SchemaRest.ListAlgorithmInfoResponse.WRITE);
    }
}
