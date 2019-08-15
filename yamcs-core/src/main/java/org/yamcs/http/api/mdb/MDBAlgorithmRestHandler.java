package org.yamcs.http.api.mdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.Processor;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.ChangeAlgorithmRequest;
import org.yamcs.protobuf.Mdb.ListAlgorithmsResponse;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to algorithm info from the MDB
 */
public class MDBAlgorithmRestHandler extends RestHandler {

    private static final Log log = new Log(MDBAlgorithmRestHandler.class);

    @Route(path = "/api/mdb/{instance}/algorithms", method = "GET")
    public void listAlgorithms(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.GetMissionDatabase);

        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));
        }

        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);

        List<Algorithm> matchedAlgorithms = new ArrayList<>();
        if (req.hasQueryParameter("namespace")) {
            String namespace = req.getQueryParameter("namespace");

            for (Algorithm algo : mdb.getAlgorithms()) {
                if (matcher != null && !matcher.matches(algo)) {
                    continue;
                }

                String alias = algo.getAlias(namespace);
                if (alias != null || (recurse && algo.getQualifiedName().startsWith(namespace))) {
                    matchedAlgorithms.add(algo);
                }
            }
        } else { // List all
            for (Algorithm algo : mdb.getAlgorithms()) {
                if (matcher != null && !matcher.matches(algo)) {
                    continue;
                }
                matchedAlgorithms.add(algo);
            }
        }

        Collections.sort(matchedAlgorithms, (p1, p2) -> {
            return p1.getQualifiedName().compareTo(p2.getQualifiedName());
        });

        int totalSize = matchedAlgorithms.size();

        String next = req.getQueryParameter("next", null);
        int pos = req.getQueryParameterAsInt("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            matchedAlgorithms = matchedAlgorithms.stream().filter(p -> {
                return p.getQualifiedName().compareTo(pageToken.name) > 0;
            }).collect(Collectors.toList());
        } else if (pos > 0) {
            matchedAlgorithms = matchedAlgorithms.subList(pos, matchedAlgorithms.size());
        }

        NamedObjectPageToken continuationToken = null;
        if (limit < matchedAlgorithms.size()) {
            matchedAlgorithms = matchedAlgorithms.subList(0, limit);
            Algorithm lastAlgorithm = matchedAlgorithms.get(limit - 1);
            continuationToken = new NamedObjectPageToken(lastAlgorithm.getQualifiedName());
        }

        ListAlgorithmsResponse.Builder responseb = ListAlgorithmsResponse.newBuilder();
        responseb.setTotalSize(totalSize);
        for (Algorithm a : matchedAlgorithms) {
            responseb.addAlgorithm(XtceToGpbAssembler.toAlgorithmInfo(a, DetailLevel.SUMMARY));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/mdb/{instance}/algorithms/{name*}", method = "GET")
    public void getAlgorithm(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.GetMissionDatabase);
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Algorithm algo = verifyAlgorithm(req, mdb, req.getRouteParam("name"));

        AlgorithmInfo cinfo = XtceToGpbAssembler.toAlgorithmInfo(algo, DetailLevel.FULL);
        completeOK(req, cinfo);
    }

    @Route(path = "/api/mdb/{instance}/{processor}/algorithms/{name*}", method = { "PATCH", "PUT", "POST" })
    public void setAlgorithm(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ChangeMissionDatabase);

        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        List<AlgorithmManager> l = processor.getServices(AlgorithmManager.class);
        if (l.size() == 0) {
            throw new BadRequestException("No AlgorithmManager available for this processor");
        }
        if (l.size() > 1) {
            throw new BadRequestException(
                    "Cannot patch algorithm when a processor has more than 1 AlgorithmManager services");
        }
        AlgorithmManager algMng = l.get(0);
        XtceDb xtcedb = XtceDbFactory.getInstance(processor.getInstance());
        Algorithm a = verifyAlgorithm(req, xtcedb, req.getRouteParam("name"));
        if (!(a instanceof CustomAlgorithm)) {
            throw new BadRequestException("Can only patch CustomAlgorithm instances");
        }
        CustomAlgorithm calg = (CustomAlgorithm) a;
        ChangeAlgorithmRequest car = req.bodyAsMessage(ChangeAlgorithmRequest.newBuilder()).build();
        log.debug("received ChangeAlgorithmRequest {}", car);
        switch (car.getAction()) {
        case RESET:
            algMng.clearAlgorithmOverride(calg);
            break;
        case SET:
            if (!car.hasAlgorithm()) {
                throw new BadRequestException("No algorithm info provided");
            }
            AlgorithmInfo ai = car.getAlgorithm();
            if (!ai.hasText()) {
                throw new BadRequestException("No algorithm text provided");
            }
            try {
                log.debug("Setting text for algorithm {} to {}", calg.getQualifiedName(), ai.getText());
                algMng.setAlgorithmText(calg, ai.getText());
            } catch (Exception e) {
                throw new BadRequestException(e.getMessage());
            }
            break;
        default:
            throw new BadRequestException("Unknown action " + car.getAction());
        }

        completeOK(req);
    }
}
