package org.yamcs.web.rest.mdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Rest.ListContainerInfoResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.XtceToGpbAssembler;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper.MatchResult;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to container info from the MDB
 */
public class MDBContainerRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(MDBContainerRequestHandler.class.getName());
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        if (!req.hasPathSegment(pathOffset)) {
            return listContainers(req, null, mdb); // root namespace
        } else {
            MatchResult<SequenceContainer> pm = MissionDatabaseHelper.matchContainerName(req, pathOffset);
            if (pm.matches()) { // container
                return getSingleContainer(req, pm.getRequestedId(), pm.getMatch());
            } else { // namespace
                return listContainersOrError(req, pathOffset);
            }
        }
    }
    
    private RestResponse listContainersOrError(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        MatchResult<String> nsm = MissionDatabaseHelper.matchXtceDbNamespace(req, pathOffset, true);
        if (nsm.matches()) {
            return listContainers(req, nsm.getMatch(), mdb);
        } else {
            throw new NotFoundException(req);
        }
    }
    
    private RestResponse getSingleContainer(RestRequest req, NamedObjectId id, SequenceContainer c) throws RestException {
        // TODO privileges
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        ContainerInfo cinfo = XtceToGpbAssembler.toContainerInfo(c, instanceURL, DetailLevel.FULL, req.getOptions());
        return new RestResponse(req, cinfo, SchemaMdb.ContainerInfo.WRITE);
    }

    /**
     * Sends the containers for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private RestResponse listContainers(RestRequest req, String namespace, XtceDb mdb) throws RestException {
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListContainerInfoResponse.Builder responseb = ListContainerInfoResponse.newBuilder();
        if (namespace == null) {
            for (SequenceContainer c : mdb.getSequenceContainers()) {
                if (matcher != null && !matcher.matches(c)) continue;
                responseb.addContainer(XtceToGpbAssembler.toContainerInfo(c, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
            }
        } else {
            // TODO privileges
            for (SequenceContainer c : mdb.getSequenceContainers()) {
                if (matcher != null && !matcher.matches(c))
                    continue;
                
                String alias = c.getAlias(namespace);
                if (alias != null || (recurse && c.getQualifiedName().startsWith(namespace))) {
                    responseb.addContainer(XtceToGpbAssembler.toContainerInfo(c, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
                }
            }
        }
        
        return new RestResponse(req, responseb.build(), SchemaRest.ListContainerInfoResponse.WRITE);
    }
}
