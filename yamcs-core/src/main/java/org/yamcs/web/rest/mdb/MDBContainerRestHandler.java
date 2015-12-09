package org.yamcs.web.rest.mdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Rest.ListContainerInfoResponse;
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
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

import io.netty.channel.ChannelFuture;

/**
 * Handles incoming requests related to container info from the MDB
 */
public class MDBContainerRestHandler extends RestHandler {
    final static Logger log = LoggerFactory.getLogger(MDBContainerRestHandler.class.getName());
    
    @Override
    public ChannelFuture handleRequest(RestRequest req, int pathOffset) throws HttpException {
        XtceDb mdb = req.getFromContext(MDBRestHandler.CTX_MDB);
        if (!req.hasPathSegment(pathOffset)) {
            return listContainers(req, null, mdb); // root namespace
        } else {
            MatchResult<SequenceContainer> pm = MDBHelper.matchContainerName(req, pathOffset);
            if (pm.matches()) { // container
                return getSingleContainer(req, pm.getRequestedId(), pm.getMatch());
            } else { // namespace
                return listContainersOrError(req, pathOffset);
            }
        }
    }
    
    private ChannelFuture listContainersOrError(RestRequest req, int pathOffset) throws HttpException {
        XtceDb mdb = req.getFromContext(MDBRestHandler.CTX_MDB);
        MatchResult<String> nsm = MDBHelper.matchXtceDbNamespace(req, pathOffset, true);
        if (nsm.matches()) {
            return listContainers(req, nsm.getMatch(), mdb);
        } else {
            throw new NotFoundException(req);
        }
    }
    
    private ChannelFuture getSingleContainer(RestRequest req, NamedObjectId id, SequenceContainer c) throws HttpException {
        // TODO privileges
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        ContainerInfo cinfo = XtceToGpbAssembler.toContainerInfo(c, instanceURL, DetailLevel.FULL, req.getOptions());
        return sendOK(req, cinfo, SchemaMdb.ContainerInfo.WRITE);
    }

    /**
     * Sends the containers for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private ChannelFuture listContainers(RestRequest req, String namespace, XtceDb mdb) throws HttpException {
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
        
        return sendOK(req, responseb.build(), SchemaRest.ListContainerInfoResponse.WRITE);
    }
}
