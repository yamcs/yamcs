package org.yamcs.http.api.mdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.http.HttpException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.protobuf.Mdb.ListParameterTypesResponse;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to parameter type info from the MDB
 */
public class MDBParameterTypeRestHandler extends RestHandler {
    final static Logger log = LoggerFactory.getLogger(MDBParameterTypeRestHandler.class);

    @Route(rpc = "yamcs.protobuf.mdb.MDB.GetParameterType")
    public void getParameterTypeInfo(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        ParameterType p = verifyParameterType(req, mdb, req.getRouteParam("name"));

        ParameterTypeInfo pinfo = XtceToGpbAssembler.toParameterTypeInfo(p, DetailLevel.FULL);
        completeOK(req, pinfo);
    }

    @Route(rpc = "yamcs.protobuf.mdb.MDB.ListParameterTypes")
    public void listParameterTypes(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));
        }

        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);
        boolean details = req.getQueryParameterAsBoolean("details", false);

        List<ParameterType> matchedTypes = new ArrayList<>();
        if (req.hasQueryParameter("namespace")) {
            String namespace = req.getQueryParameter("namespace");
            for (ParameterType t : mdb.getParameterTypes()) {
                if (matcher != null && !matcher.matches((NameDescription) t)) {
                    continue;
                }

                String alias = ((NameDescription) t).getAlias(namespace);
                if (alias != null || (recurse && ((NameDescription) t).getQualifiedName().startsWith(namespace))) {
                    matchedTypes.add(t);
                }
            }
        } else { // List all
            for (ParameterType t : mdb.getParameterTypes()) {
                if (matcher != null && !matcher.matches((NameDescription) t)) {
                    continue;
                }
                matchedTypes.add(t);
            }
        }

        Collections.sort(matchedTypes, (t1, t2) -> {
            return ((NameDescription) t1).getQualifiedName().compareTo(((NameDescription) t2).getQualifiedName());
        });

        int totalSize = matchedTypes.size();

        String next = req.getQueryParameter("next", null);
        int pos = req.getQueryParameterAsInt("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            matchedTypes = matchedTypes.stream().filter(t -> {
                return ((NameDescription) t).getQualifiedName().compareTo(pageToken.name) > 0;
            }).collect(Collectors.toList());
        } else if (pos > 0) {
            matchedTypes = matchedTypes.subList(pos, matchedTypes.size());
        }

        NamedObjectPageToken continuationToken = null;
        if (limit < matchedTypes.size()) {
            matchedTypes = matchedTypes.subList(0, limit);
            ParameterType lastType = matchedTypes.get(limit - 1);
            continuationToken = new NamedObjectPageToken(((NameDescription) lastType).getQualifiedName());
        }

        ListParameterTypesResponse.Builder responseb = ListParameterTypesResponse.newBuilder();
        responseb.setTotalSize(totalSize);
        for (ParameterType t : matchedTypes) {
            responseb.addTypes(
                    XtceToGpbAssembler.toParameterTypeInfo(t, details ? DetailLevel.FULL : DetailLevel.SUMMARY));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        completeOK(req, responseb.build());
    }
}
