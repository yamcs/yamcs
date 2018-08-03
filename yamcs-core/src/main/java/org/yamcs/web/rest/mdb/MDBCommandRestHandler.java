package org.yamcs.web.rest.mdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ListCommandsResponse;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to command info from the MDB
 */
public class MDBCommandRestHandler extends RestHandler {

    @Route(path = "/api/mdb/:instance/commands", method = "GET")
    @Route(path = "/api/mdb/:instance/commands/:name*", method = "GET")
    public void getCommand(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.GetMissionDatabase);

        if (req.hasRouteParam("name")) {
            getCommandInfo(req);
        } else {
            listCommands(req);
        }
    }

    private void getCommandInfo(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        MetaCommand cmd = verifyCommand(req, mdb, req.getRouteParam("name"));

        CommandInfo cinfo = XtceToGpbAssembler.toCommandInfo(cmd, DetailLevel.FULL);
        completeOK(req, cinfo);
    }

    private void listCommands(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));
        }

        boolean details = req.getQueryParameterAsBoolean("details", false);
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);

        DetailLevel detailLevel = details ? DetailLevel.FULL : DetailLevel.SUMMARY;

        List<MetaCommand> matchedCommands = new ArrayList<>();
        if (req.hasQueryParameter("namespace")) {
            String namespace = req.getQueryParameter("namespace");
            for (MetaCommand cmd : mdb.getMetaCommands()) {
                if (!hasObjectPrivilege(req, ObjectPrivilegeType.Command, cmd.getQualifiedName())) {
                    continue;
                }
                if (matcher != null && !matcher.matches(cmd)) {
                    continue;
                }

                String alias = cmd.getAlias(namespace);
                if (alias != null || (recurse && cmd.getQualifiedName().startsWith(namespace))) {
                    matchedCommands.add(cmd);
                }
            }
        } else { // List all
            for (MetaCommand cmd : mdb.getMetaCommands()) {
                if (matcher != null && !matcher.matches(cmd)) {
                    continue;
                }
                matchedCommands.add(cmd);
            }
        }

        Collections.sort(matchedCommands, (p1, p2) -> {
            return p1.getQualifiedName().compareTo(p2.getQualifiedName());
        });

        int totalSize = matchedCommands.size();

        String next = req.getQueryParameter("next", null);
        int limit = req.getQueryParameterAsInt("limit", 100);
        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            matchedCommands = matchedCommands.stream().filter(p -> {
                return p.getQualifiedName().compareTo(pageToken.name) > 0;
            }).collect(Collectors.toList());
        }

        NamedObjectPageToken continuationToken = null;
        if (limit < matchedCommands.size()) {
            matchedCommands = matchedCommands.subList(0, limit);
            MetaCommand lastCommand = matchedCommands.get(limit - 1);
            continuationToken = new NamedObjectPageToken(lastCommand.getQualifiedName());
        }

        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        responseb.setTotalSize(totalSize);
        for (MetaCommand c : matchedCommands) {
            responseb.addCommand(XtceToGpbAssembler.toCommandInfo(c, detailLevel));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        completeOK(req, responseb.build());
    }
}
