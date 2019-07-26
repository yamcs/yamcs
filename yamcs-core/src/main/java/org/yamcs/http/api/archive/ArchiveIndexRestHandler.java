package org.yamcs.http.api.archive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexServer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.RestRequest.IntervalResult;
import org.yamcs.protobuf.Archive.IndexEntry;
import org.yamcs.protobuf.Archive.IndexGroup;
import org.yamcs.protobuf.Archive.IndexResponse;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;

import io.netty.handler.codec.http.HttpResponseStatus;

public class ArchiveIndexRestHandler extends RestHandler {

    @Route(path = "/api/archive/:instance/command-index", method = "GET")
    public void listCommandIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int mergeTime = req.getQueryParameterAsInt("mergeTime", 2000);
        int limit = req.getQueryParameterAsInt("limit", 500);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        String next = req.getQueryParameter("next", null);

        if (req.hasQueryParameter("name")) {
            for (String names : req.getQueryParameterList("name")) {
                for (String name : names.split(",")) {
                    requestb.addCmdName(NamedObjectId.newBuilder().setName(name.trim()));
                }
            }
        } else {
            requestb.setSendAllCmd(true);
        }

        handleOneIndexResult(req, indexServer, requestb.build(), limit, next);
    }

    @Route(path = "/api/archive/:instance/event-index", method = "GET")
    public void listEventIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int mergeTime = req.getQueryParameterAsInt("mergeTime", 2000);
        int limit = req.getQueryParameterAsInt("limit", 500);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        String next = req.getQueryParameter("next", null);

        if (req.hasQueryParameter("source")) {
            for (String sources : req.getQueryParameterList("source")) {
                for (String source : sources.split(",")) {
                    requestb.addEventSource(NamedObjectId.newBuilder().setName(source.trim()));
                }
            }
        } else {
            requestb.setSendAllEvent(true);
        }

        handleOneIndexResult(req, indexServer, requestb.build(), limit, next);
    }

    @Route(path = "/api/archive/:instance/packet-index", method = "GET")
    public void listPacketIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int mergeTime = req.getQueryParameterAsInt("mergeTime", 2000);
        int limit = req.getQueryParameterAsInt("limit", 500);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        String next = req.getQueryParameter("next", null);

        if (req.hasQueryParameter("name")) {
            for (String names : req.getQueryParameterList("name")) {
                for (String name : names.split(",")) {
                    requestb.addTmPacket(NamedObjectId.newBuilder().setName(name.trim()));
                }
            }
        } else {
            requestb.setSendAllTm(true);
        }

        handleOneIndexResult(req, indexServer, requestb.build(), limit, next);
    }

    @Route(path = "/api/archive/:instance/parameter-index", method = "GET")
    public void listParameterIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int mergeTime = req.getQueryParameterAsInt("mergeTime", 20000);
        int limit = req.getQueryParameterAsInt("limit", 500);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        String next = req.getQueryParameter("next", null);
        if (req.hasQueryParameter("group")) {
            for (String groups : req.getQueryParameterList("group")) {
                for (String group : groups.split(",")) {
                    requestb.addPpGroup(NamedObjectId.newBuilder().setName(group.trim()));
                }
            }
        } else {
            requestb.setSendAllPp(true);
        }

        handleOneIndexResult(req, indexServer, requestb.build(), limit, next);
    }

    @Route(path = "/api/archive/:instance/completeness-index", method = "GET")
    public void listCompletenessIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);
        int limit = req.getQueryParameterAsInt("limit", 500);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setSendCompletenessIndex(true);
        requestb.setInstance(instance);

        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }
        String next = req.getQueryParameter("next", null);
        handleOneIndexResult(req, indexServer, requestb.build(), limit, next);
    }

    private IndexServer verifyIndexServer(RestRequest req, String instance) throws HttpException {
        verifyInstance(req, instance);
        List<IndexServer> services = yamcsServer.getServices(instance, IndexServer.class);
        if (services.isEmpty()) {
            throw new BadRequestException("Index service not enabled for instance '" + instance + "'");
        } else {
            return services.get(0);
        }
    }

    /**
     * Submits an index request but returns only the first batch of results combined with a pagination token if the user
     * wishes to retrieve the next batch.
     * 
     * The batch size is determined by the IndexServer and is set to 500 (shared between all requested groups).
     */
    private void handleOneIndexResult(RestRequest req, IndexServer indexServer, IndexRequest request, int limit,
            String token)
            throws HttpException {
        try {
            IndexResponse.Builder responseb = IndexResponse.newBuilder();
            Map<NamedObjectId, IndexGroup.Builder> groupBuilders = new HashMap<>();
            indexServer.submitIndexRequest(request, limit, token, new IndexRequestListener() {

                long last;

                @Override
                public void processData(ArchiveRecord rec) {
                    IndexGroup.Builder groupb = groupBuilders.get(rec.getId());
                    if (groupb == null) {
                        groupb = IndexGroup.newBuilder().setId(rec.getId());
                        groupBuilders.put(rec.getId(), groupb);
                    }
                    IndexEntry.Builder ieb = IndexEntry.newBuilder()
                            .setStart(TimeEncoding.toString(rec.getFirst()))
                            .setStop(TimeEncoding.toString(rec.getLast()))
                            .setCount(rec.getNum());
                    if (rec.hasSeqFirst()) {
                        ieb.setSeqStart(rec.getSeqFirst());
                    }
                    if (rec.hasSeqLast()) {
                        ieb.setSeqStop(rec.getSeqLast());
                    }
                    groupb.addEntry(ieb);
                    last = Math.max(last, rec.getLast());
                }

                @Override
                public void finished(String token, boolean success) {
                    if (success) {
                        if (token != null) {
                            responseb.setContinuationToken(token);
                        }
                        List<IndexGroup.Builder> sortedGroups = new ArrayList<>(groupBuilders.values());
                        Collections.sort(sortedGroups, (g1, g2) -> {
                            return g1.getId().getName().compareTo(g2.getId().getName());
                        });
                        sortedGroups.forEach(groupb -> responseb.addGroup(groupb));
                        completeOK(req, responseb.build());
                    } else {
                        sendRestError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                new InternalServerErrorException("Too many results"));
                    }
                }
            });
        } catch (YamcsException e) {
            throw new InternalServerErrorException("Too many results", e);
        }
    }

}
