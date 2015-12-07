package org.yamcs.web.rest.mdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Rest.ListCommandInfoResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.security.Privilege.Type;
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.XtceToGpbAssembler;
import org.yamcs.web.rest.XtceToGpbAssembler.DetailLevel;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper.MatchResult;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;

/**
 * Handles incoming requests related to command info from the MDB
 */
public class MDBCommandRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(MDBCommandRequestHandler.class.getName());
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        if (!req.hasPathSegment(pathOffset)) {
            return listCommands(req, null, mdb); // root namespace
        } else {
            MatchResult<MetaCommand> c = MissionDatabaseHelper.matchCommandName(req, pathOffset);
            if (c.matches()) { // command
                return getSingleCommand(req, c.getRequestedId(), c.getMatch());
            } else { // namespace
                return listCommandsOrError(req, pathOffset);
            }
        }
    }
    
    private RestResponse listCommandsOrError(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = req.getFromContext(MDBRequestHandler.CTX_MDB);
        MatchResult<String> nsm = MissionDatabaseHelper.matchXtceDbNamespace(req, pathOffset, true);
        if (nsm.matches()) {
            return listCommands(req, nsm.getMatch(), mdb);
        } else {
            throw new NotFoundException(req);
        }
    }
    
    private RestResponse getSingleCommand(RestRequest req, NamedObjectId id, MetaCommand cmd) throws RestException {
        if (!Privilege.getInstance().hasPrivilege(req.getAuthToken(), Privilege.Type.TC, cmd.getQualifiedName())) {
            log.warn("Command Info for {} not authorized for token {}, throwing BadRequestException", id, req.getAuthToken());
            throw new BadRequestException("Invalid command name specified "+id);
        }
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        CommandInfo cinfo = XtceToGpbAssembler.toCommandInfo(cmd, instanceURL, DetailLevel.FULL, req.getOptions());
        return new RestResponse(req, cinfo, SchemaMdb.CommandInfo.WRITE);
    }

    /**
     * Sends the commands for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private RestResponse listCommands(RestRequest req, String namespace, XtceDb mdb) throws RestException {
        String instanceURL = req.getApiURL() + "/mdb/" + req.getFromContext(RestRequest.CTX_INSTANCE);
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListCommandInfoResponse.Builder responseb = ListCommandInfoResponse.newBuilder();
        if (namespace == null) {
            for (MetaCommand cmd : mdb.getMetaCommands()) {
                if (matcher != null && !matcher.matches(cmd)) continue;
                responseb.addCommand(XtceToGpbAssembler.toCommandInfo(cmd, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
            }
        } else {
            Privilege privilege = Privilege.getInstance();
            for (MetaCommand cmd : mdb.getMetaCommands()) {
                if (!privilege.hasPrivilege(req.getAuthToken(), Type.TC, cmd.getQualifiedName()))
                    continue;
                if (matcher != null && !matcher.matches(cmd))
                    continue;
                
                String alias = cmd.getAlias(namespace);
                if (alias != null || (recurse && cmd.getQualifiedName().startsWith(namespace))) {
                    responseb.addCommand(XtceToGpbAssembler.toCommandInfo(cmd, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
                }
            }
        }
        
        return new RestResponse(req, responseb.build(), SchemaRest.ListCommandInfoResponse.WRITE);
    }
}
