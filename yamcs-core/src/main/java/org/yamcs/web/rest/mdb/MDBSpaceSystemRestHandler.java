package org.yamcs.web.rest.mdb;

import org.yamcs.protobuf.Rest.ListSpaceSystemInfoResponse;
import org.yamcs.protobuf.YamcsManagement.SpaceSystemInfo;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to space system info from the MDB
 */
public class MDBSpaceSystemRestHandler extends RestHandler {

    @Route(path = "/api/mdb/:instance/space-systems", method = "GET")
    @Route(path = "/api/mdb/:instance/space-systems/:name*", method = "GET")
    public void getSpaceSystem(RestRequest req) throws HttpException {
        if (req.hasRouteParam("name")) {
            getSpaceSystemInfo(req);
        } else {
            listSpaceSystems(req);
        }
    }

    private void getSpaceSystemInfo(RestRequest req) throws HttpException {
        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayGetMissionDatabase);

        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        SpaceSystem spaceSystem = verifySpaceSystem(req, mdb, req.getRouteParam("name"));

        String instanceURL = req.getApiURL() + "/mdb/" + instance;
        SpaceSystemInfo info = XtceToGpbAssembler.toSpaceSystemInfo(req, instanceURL, spaceSystem);
        completeOK(req, info);
    }

    private void listSpaceSystems(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));
        }

        String instanceURL = req.getApiURL() + "/mdb/" + instance;
        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);

        ListSpaceSystemInfoResponse.Builder responseb = ListSpaceSystemInfoResponse.newBuilder();
        if (req.hasQueryParameter("namespace")) {
            String namespace = req.getQueryParameter("namespace");

            for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                if (matcher != null && !matcher.matches(spaceSystem))
                    continue;

                String alias = spaceSystem.getAlias(namespace);
                if (alias != null || (recurse && spaceSystem.getQualifiedName().startsWith(namespace))) {
                    responseb.addSpaceSystem(XtceToGpbAssembler.toSpaceSystemInfo(req, instanceURL, spaceSystem));
                }
            }
        } else { // List all
            for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                if (matcher != null && !matcher.matches(spaceSystem))
                    continue;
                responseb.addSpaceSystem(XtceToGpbAssembler.toSpaceSystemInfo(req, instanceURL, spaceSystem));
            }
        }

        completeOK(req, responseb.build());
    }
}
