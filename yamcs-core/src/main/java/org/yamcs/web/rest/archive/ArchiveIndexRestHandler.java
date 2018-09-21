package org.yamcs.web.rest.archive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexServer;
import org.yamcs.protobuf.Archive.IndexEntry;
import org.yamcs.protobuf.Archive.IndexGroup;
import org.yamcs.protobuf.Archive.IndexResponse;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.Route;

import io.netty.handler.codec.http.HttpResponseStatus;

public class ArchiveIndexRestHandler extends RestHandler {

    @Route(path = "/api/archive/:instance/command-index", method = "GET")
    public void listCommandIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int mergeTime = req.getQueryParameterAsInt("mergeTime", 2000);

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
        if (next != null) {
            TimeSortedPageToken pageToken = TimeSortedPageToken.decode(next);
            requestb.setStart(pageToken.time);
        }

        if (req.hasQueryParameter("name")) {
            for (String names : req.getQueryParameterList("name")) {
                for (String name : names.split(",")) {
                    requestb.addCmdName(NamedObjectId.newBuilder().setName(name.trim()));
                }
            }
        } else {
            requestb.setSendAllCmd(true);
        }

        handleOneIndexResult(req, indexServer, requestb.build());
    }

    @Route(path = "/api/archive/:instance/event-index", method = "GET")
    public void listEventIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int mergeTime = req.getQueryParameterAsInt("mergeTime", 2000);

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
        if (next != null) {
            TimeSortedPageToken pageToken = TimeSortedPageToken.decode(next);
            requestb.setStart(pageToken.time);
        }

        if (req.hasQueryParameter("source")) {
            for (String sources : req.getQueryParameterList("source")) {
                for (String source : sources.split(",")) {
                    requestb.addEventSource(NamedObjectId.newBuilder().setName(source.trim()));
                }
            }
        } else {
            requestb.setSendAllEvent(true);
        }

        handleOneIndexResult(req, indexServer, requestb.build());
    }

    @Route(path = "/api/archive/:instance/packet-index", method = "GET")
    public void listPacketIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int mergeTime = req.getQueryParameterAsInt("mergeTime", 2000);

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
        if (next != null) {
            TimeSortedPageToken pageToken = TimeSortedPageToken.decode(next);
            requestb.setStart(pageToken.time);
        }

        if (req.hasQueryParameter("name")) {
            for (String names : req.getQueryParameterList("name")) {
                for (String name : names.split(",")) {
                    requestb.addTmPacket(NamedObjectId.newBuilder().setName(name.trim()));
                }
            }
        } else {
            requestb.setSendAllTm(true);
        }

        handleOneIndexResult(req, indexServer, requestb.build());
    }

    @Route(path = "/api/archive/:instance/parameter-index", method = "GET")
    public void listParameterIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int mergeTime = req.getQueryParameterAsInt("mergeTime", 20000);

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
        if (next != null) {
            TimeSortedPageToken pageToken = TimeSortedPageToken.decode(next);
            requestb.setStart(pageToken.time);
        }

        if (req.hasQueryParameter("group")) {
            for (String groups : req.getQueryParameterList("group")) {
                for (String group : groups.split(",")) {
                    requestb.addPpGroup(NamedObjectId.newBuilder().setName(group.trim()));
                }
            }
        } else {
            requestb.setSendAllPp(true);
        }

        handleOneIndexResult(req, indexServer, requestb.build());
    }

    @Route(path = "/api/archive/:instance/completeness-index", method = "GET")
    public void listCompletenessIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

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
        if (next != null) {
            TimeSortedPageToken pageToken = TimeSortedPageToken.decode(next);
            requestb.setStart(pageToken.time);
        }

        handleOneIndexResult(req, indexServer, requestb.build());
    }

    private IndexServer verifyIndexServer(RestRequest req, String instance) throws HttpException {
        verifyInstance(req, instance);
        List<IndexServer> services = YamcsServer.getServices(instance, IndexServer.class);
        if (services.isEmpty()) {
            throw new BadRequestException("Index service not enabled for instance '" + instance + "'");
        } else {
            return services.get(0);
        }
    }

    /**
     * Submits an index request but returns only the first batch of results combined with a pagination token if the user
     * whishes to retrieve the next batch.
     * 
     * The batch size is determined by the IndexServer and is set to 500 (shared between all requested groups).
     */
    private void handleOneIndexResult(RestRequest req, IndexServer indexServer, IndexRequest request)
            throws HttpException {
        try {
            IndexResponse.Builder responseb = IndexResponse.newBuilder();
            Map<NamedObjectId, IndexGroup.Builder> groupBuilders = new HashMap<>();
            indexServer.submitIndexRequest(request, new IndexRequestListener() {

                int batchCount = 0;
                long last;

                @Override
                public void processData(IndexResult indexResult) {
                    if (batchCount == 0) {
                        for (ArchiveRecord rec : indexResult.getRecordsList()) {
                            IndexGroup.Builder groupb = groupBuilders.get(rec.getId());
                            if (groupb == null) {
                                groupb = IndexGroup.newBuilder().setId(rec.getId());
                                groupBuilders.put(rec.getId(), groupb);
                            }
                            groupb.addEntry(IndexEntry.newBuilder()
                                    .setStart(TimeEncoding.toString(rec.getFirst()))
                                    .setStop(TimeEncoding.toString(rec.getLast()))
                                    .setCount(rec.getNum()));
                            last = Math.max(last, rec.getLast());
                        }
                    }

                    batchCount++;
                }

                @Override
                public void finished(boolean success) {
                    if (success) {
                        if (batchCount > 1) {
                            TimeSortedPageToken token = new TimeSortedPageToken(last);
                            responseb.setContinuationToken(token.encodeAsString());
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
