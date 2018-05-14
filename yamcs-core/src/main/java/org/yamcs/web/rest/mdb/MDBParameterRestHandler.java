package org.yamcs.web.rest.mdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Rest.BulkGetParameterInfoRequest;
import org.yamcs.protobuf.Rest.BulkGetParameterInfoResponse;
import org.yamcs.protobuf.Rest.BulkGetParameterInfoResponse.GetParameterInfoResponse;
import org.yamcs.protobuf.Rest.ListParameterInfoResponse;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.security.PrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to parameter info from the MDB
 */
public class MDBParameterRestHandler extends RestHandler {
    final static Logger log = LoggerFactory.getLogger(MDBParameterRestHandler.class);

    @Route(path = "/api/mdb/:instance/parameters/bulk", method = { "GET", "POST" }, priority = true)
    public void getBulkParameterInfo(RestRequest req) throws HttpException {
        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayGetMissionDatabase);

        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        BulkGetParameterInfoRequest request = req.bodyAsMessage(BulkGetParameterInfoRequest.newBuilder()).build();
        BulkGetParameterInfoResponse.Builder responseb = BulkGetParameterInfoResponse.newBuilder();
        for (NamedObjectId id : request.getIdList()) {
            Parameter p = mdb.getParameter(id);
            if (p == null) {
                throw new BadRequestException("Invalid parameter name specified " + id);
            }
            if (!Privilege.getInstance().hasPrivilege1(req.getAuthToken(), PrivilegeType.TM_PARAMETER,
                    p.getQualifiedName())) {
                log.warn("Not providing information about parameter {} because no privileges exists",
                        p.getQualifiedName());
                continue;
            }

            GetParameterInfoResponse.Builder response = GetParameterInfoResponse.newBuilder();
            response.setId(id);
            response.setParameter(XtceToGpbAssembler.toParameterInfo(p, DetailLevel.SUMMARY));
            responseb.addResponse(response);
        }

        completeOK(req, responseb.build());
    }

    @Route(path = "/api/mdb/:instance/parameters", method = "GET")
    @Route(path = "/api/mdb/:instance/parameters/:name*", method = "GET")
    public void getParameter(RestRequest req) throws HttpException {
        if (req.hasRouteParam("name")) {
            getParameterInfo(req);
        } else {
            listParameters(req);
        }
    }

    private void getParameterInfo(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));

        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(p, DetailLevel.FULL);
        completeOK(req, pinfo);
    }

    private void listParameters(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));
        }

        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        boolean details = req.getQueryParameterAsBoolean("details", false);

        // Support both type[]=float&type[]=integer and type=float,integer
        Set<String> types = new HashSet<>();
        if (req.hasQueryParameter("type")) {
            for (String type : req.getQueryParameterList("type")) {
                for (String t : type.split(",")) {
                    if (!"all".equalsIgnoreCase(t)) {
                        types.add(t.toLowerCase());
                    }
                }
            }
        }

        List<Parameter> matchedParameters = new ArrayList<>();
        if (req.hasQueryParameter("namespace")) {
            String namespace = req.getQueryParameter("namespace");

            Privilege privilege = Privilege.getInstance();
            for (Parameter p : mdb.getParameters()) {
                if (!privilege.hasPrivilege1(req.getAuthToken(), PrivilegeType.TM_PARAMETER, p.getQualifiedName())) {
                    continue;
                }
                if (matcher != null && !matcher.matches(p)) {
                    continue;
                }

                String alias = p.getAlias(namespace);
                if (alias != null || (recurse && p.getQualifiedName().startsWith(namespace))) {
                    if (parameterTypeMatches(p, types)) {
                        matchedParameters.add(p);
                    }
                }
            }
        } else { // List all
            for (Parameter p : mdb.getParameters()) {
                if (matcher != null && !matcher.matches(p)) {
                    continue;
                }
                if (parameterTypeMatches(p, types)) {
                    matchedParameters.add(p);
                }
            }
        }

        Collections.sort(matchedParameters, (p1, p2) -> {
            return p1.getQualifiedName().compareTo(p2.getQualifiedName());
        });

        if (req.hasQueryParameter("limit")) {
            int limit = req.getQueryParameterAsInt("limit");
            if (limit < matchedParameters.size()) {
                matchedParameters = matchedParameters.subList(0, limit);
            }
        }

        ListParameterInfoResponse.Builder responseb = ListParameterInfoResponse.newBuilder();
        for (Parameter p : matchedParameters) {
            responseb.addParameter(
                    XtceToGpbAssembler.toParameterInfo(p, details ? DetailLevel.FULL : DetailLevel.SUMMARY));
        }
        completeOK(req, responseb.build());
    }

    private boolean parameterTypeMatches(Parameter p, Set<String> types) {
        if (types.isEmpty()) {
            return true;
        }
        return p.getParameterType() != null
                && types.contains(p.getParameterType().getTypeAsString());
    }
}
