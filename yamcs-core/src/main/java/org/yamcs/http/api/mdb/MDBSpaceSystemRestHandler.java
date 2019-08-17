package org.yamcs.http.api.mdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.http.HttpException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.protobuf.Mdb.ListSpaceSystemsResponse;
import org.yamcs.protobuf.Mdb.SpaceSystemInfo;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to space system info from the MDB
 */
public class MDBSpaceSystemRestHandler extends RestHandler {

    @Route(rpc = "yamcs.protobuf.mdb.MDB.GetSpaceSystem")
    public void getSpaceSystem(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.GetMissionDatabase);

        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        SpaceSystem spaceSystem = verifySpaceSystem(req, mdb, req.getRouteParam("name"));

        SpaceSystemInfo info = XtceToGpbAssembler.toSpaceSystemInfo(req, spaceSystem);
        completeOK(req, info);
    }

    @Route(rpc = "yamcs.protobuf.mdb.MDB.ListSpaceSystems")
    public void listSpaceSystems(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.GetMissionDatabase);
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));
        }

        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);

        List<SpaceSystem> matchedSpaceSystems = new ArrayList<>();
        if (req.hasQueryParameter("namespace")) {
            String namespace = req.getQueryParameter("namespace");

            for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                if (matcher != null && !matcher.matches(spaceSystem)) {
                    continue;
                }

                String alias = spaceSystem.getAlias(namespace);
                if (alias != null || (recurse && spaceSystem.getQualifiedName().startsWith(namespace))) {
                    matchedSpaceSystems.add(spaceSystem);
                }
            }
        } else { // List all
            for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                if (matcher != null && !matcher.matches(spaceSystem)) {
                    continue;
                }
                matchedSpaceSystems.add(spaceSystem);
            }
        }

        Collections.sort(matchedSpaceSystems, (p1, p2) -> {
            return p1.getQualifiedName().compareTo(p2.getQualifiedName());
        });

        int totalSize = matchedSpaceSystems.size();

        String next = req.getQueryParameter("next", null);
        int pos = req.getQueryParameterAsInt("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            matchedSpaceSystems = matchedSpaceSystems.stream().filter(p -> {
                return p.getQualifiedName().compareTo(pageToken.name) > 0;
            }).collect(Collectors.toList());
        } else if (pos > 0) {
            matchedSpaceSystems = matchedSpaceSystems.subList(pos, matchedSpaceSystems.size());
        }

        NamedObjectPageToken continuationToken = null;
        if (limit < matchedSpaceSystems.size()) {
            matchedSpaceSystems = matchedSpaceSystems.subList(0, limit);
            SpaceSystem lastSpaceSystem = matchedSpaceSystems.get(limit - 1);
            continuationToken = new NamedObjectPageToken(lastSpaceSystem.getQualifiedName());
        }

        ListSpaceSystemsResponse.Builder responseb = ListSpaceSystemsResponse.newBuilder();
        responseb.setTotalSize(totalSize);
        for (SpaceSystem s : matchedSpaceSystems) {
            responseb.addSpaceSystems(XtceToGpbAssembler.toSpaceSystemInfo(req, s));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        completeOK(req, responseb.build());
    }
}
