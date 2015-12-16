package org.yamcs.web.rest.mdb;

import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Rest.ListCommandInfoResponse;
import org.yamcs.protobuf.SchemaMdb;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.channel.ChannelFuture;

/**
 * Handles incoming requests related to command info from the MDB
 */
public class MDBCommandRestHandler extends RestHandler {
    
    @Route(path = "/api/mdb/:instance/commands/:name*", method = "GET")
    public ChannelFuture getCommand(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        MetaCommand cmd = verifyCommand(req, mdb, req.getRouteParam("name"));
        
        String instanceURL = req.getApiURL() + "/mdb/" + instance;
        CommandInfo cinfo = XtceToGpbAssembler.toCommandInfo(cmd, instanceURL, DetailLevel.FULL, req.getOptions());
        return sendOK(req, cinfo, SchemaMdb.CommandInfo.WRITE);
    }

    /**
     * Sends the commands for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    @Route(path = "/api/archive/:instance/commands", method = "GET")
    public ChannelFuture listCommands(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        
        String instanceURL = req.getApiURL() + "/mdb/" + instance;
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));    
        }
        
        ListCommandInfoResponse.Builder responseb = ListCommandInfoResponse.newBuilder();
        //if (namespace == null) {
            for (MetaCommand cmd : mdb.getMetaCommands()) {
                if (matcher != null && !matcher.matches(cmd)) continue;
                responseb.addCommand(XtceToGpbAssembler.toCommandInfo(cmd, instanceURL, DetailLevel.SUMMARY, req.getOptions()));
            }
        /*} else {
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
        }*/
        
        return sendOK(req, responseb.build(), SchemaRest.ListCommandInfoResponse.WRITE);
    }
}
