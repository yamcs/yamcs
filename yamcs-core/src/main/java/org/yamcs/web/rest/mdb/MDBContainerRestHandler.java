package org.yamcs.web.rest.mdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.ListContainersResponse;
import org.yamcs.protobuf.Mdb.UsedByInfo;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to container info from the MDB
 */
public class MDBContainerRestHandler extends RestHandler {

    @Route(path = "/api/mdb/:instance/containers", method = "GET")
    @Route(path = "/api/mdb/:instance/containers/:name*", method = "GET")
    public void getContainer(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.GetMissionDatabase);

        if (req.hasRouteParam("name")) {
            getContainerInfo(req);
        } else {
            listContainers(req);
        }
    }

    private void getContainerInfo(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        SequenceContainer c = verifyContainer(req, mdb, req.getRouteParam("name"));

        ContainerInfo cinfo = XtceToGpbAssembler.toContainerInfo(c, DetailLevel.FULL);
        List<ContainerEntry> containerEntries = mdb.getContainerEntries(c);
        if (containerEntries != null) {
            ContainerInfo.Builder cinfob = ContainerInfo.newBuilder(cinfo);
            Set<SequenceContainer> usingContainers = new HashSet<>();
            for (ContainerEntry entry : containerEntries) {
                Container containingContainer = entry.getContainer();
                if (containingContainer instanceof SequenceContainer) {
                    usingContainers.add((SequenceContainer) containingContainer);
                }
            }

            UsedByInfo.Builder usedByb = UsedByInfo.newBuilder();
            List<SequenceContainer> unsortedContainers = new ArrayList<>(usingContainers);
            Collections.sort(unsortedContainers, (c1, c2) -> c1.getQualifiedName().compareTo(c2.getQualifiedName()));
            for (SequenceContainer seqContainer : unsortedContainers) {
                ContainerInfo usingContainer = XtceToGpbAssembler.toContainerInfo(seqContainer, DetailLevel.LINK);
                usedByb.addContainer(usingContainer);
            }
            cinfob.setUsedBy(usedByb);
            cinfo = cinfob.build();
        }

        completeOK(req, cinfo);
    }

    private void listContainers(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (req.hasQueryParameter("q")) {
            matcher = new NameDescriptionSearchMatcher(req.getQueryParameter("q"));
        }

        boolean recurse = req.getQueryParameterAsBoolean("recurse", false);

        List<SequenceContainer> matchedContainers = new ArrayList<>();
        if (req.hasQueryParameter("namespace")) {
            String namespace = req.getQueryParameter("namespace");

            for (SequenceContainer c : mdb.getSequenceContainers()) {
                if (matcher != null && !matcher.matches(c)) {
                    continue;
                }

                String alias = c.getAlias(namespace);
                if (alias != null || (recurse && c.getQualifiedName().startsWith(namespace))) {
                    matchedContainers.add(c);
                }
            }
        } else { // List all
            for (SequenceContainer c : mdb.getSequenceContainers()) {
                if (matcher != null && !matcher.matches(c)) {
                    continue;
                }
                matchedContainers.add(c);
            }
        }

        Collections.sort(matchedContainers, (p1, p2) -> {
            return p1.getQualifiedName().compareTo(p2.getQualifiedName());
        });

        int totalSize = matchedContainers.size();

        String next = req.getQueryParameter("next", null);
        int limit = req.getQueryParameterAsInt("limit", 100);
        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            matchedContainers = matchedContainers.stream().filter(p -> {
                return p.getQualifiedName().compareTo(pageToken.name) > 0;
            }).collect(Collectors.toList());
        }

        NamedObjectPageToken continuationToken = null;
        if (limit < matchedContainers.size()) {
            matchedContainers = matchedContainers.subList(0, limit);
            SequenceContainer lastContainer = matchedContainers.get(limit - 1);
            continuationToken = new NamedObjectPageToken(lastContainer.getQualifiedName());
        }

        ListContainersResponse.Builder responseb = ListContainersResponse.newBuilder();
        responseb.setTotalSize(totalSize);
        for (SequenceContainer c : matchedContainers) {
            responseb.addContainer(XtceToGpbAssembler.toContainerInfo(c, DetailLevel.SUMMARY));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        completeOK(req, responseb.build());
    }
}
