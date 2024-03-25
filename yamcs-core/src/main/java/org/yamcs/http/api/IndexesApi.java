package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.archive.CcsdsTmIndex;
import org.yamcs.archive.IndexRequest;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexRequestProcessor;
import org.yamcs.archive.IndexRequestProcessor.InvalidTokenException;
import org.yamcs.archive.TmIndexService;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.protobuf.AbstractIndexesApi;
import org.yamcs.protobuf.IndexEntry;
import org.yamcs.protobuf.IndexGroup;
import org.yamcs.protobuf.IndexResponse;
import org.yamcs.protobuf.ListCommandHistoryIndexRequest;
import org.yamcs.protobuf.ListCompletenessIndexRequest;
import org.yamcs.protobuf.ListEventIndexRequest;
import org.yamcs.protobuf.ListPacketIndexRequest;
import org.yamcs.protobuf.ListParameterIndexRequest;
import org.yamcs.protobuf.RebuildCcsdsIndexRequest;
import org.yamcs.protobuf.StreamCommandIndexRequest;
import org.yamcs.protobuf.StreamCompletenessIndexRequest;
import org.yamcs.protobuf.StreamEventIndexRequest;
import org.yamcs.protobuf.StreamPacketIndexRequest;
import org.yamcs.protobuf.StreamParameterIndexRequest;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.YarchException;

import com.google.protobuf.Empty;

public class IndexesApi extends AbstractIndexesApi<Context> {

    @Override
    public void listCommandHistoryIndex(Context ctx, ListCommandHistoryIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TmIndexService tmIndex = getIndexService(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            indexRequest.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            indexRequest.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getNameCount() > 0) {
            for (String name : request.getNameList()) {
                indexRequest.getCommandNames().add(NamedObjectId.newBuilder().setName(name.trim()).build());
            }
        } else {
            indexRequest.setSendAllCmd(true);
        }

        handleOneIndexResult(tmIndex, indexRequest, observer, limit, next);
    }

    @Override
    public void listEventIndex(Context ctx, ListEventIndexRequest request, Observer<IndexResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TmIndexService indexServer = getIndexService(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            indexRequest.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            indexRequest.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getSourceCount() > 0) {
            for (String source : request.getSourceList()) {
                indexRequest.getEventSources().add(NamedObjectId.newBuilder().setName(source.trim()).build());
            }
        } else {
            indexRequest.setSendAllEvent(true);
        }

        handleOneIndexResult(indexServer, indexRequest, observer, limit, next);
    }

    @Override
    public void listPacketIndex(Context ctx, ListPacketIndexRequest request, Observer<IndexResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TmIndexService indexServer = getIndexService(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            indexRequest.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            indexRequest.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getNameCount() > 0) {
            for (String name : request.getNameList()) {
                indexRequest.getTmPackets().add(NamedObjectId.newBuilder().setName(name.trim()).build());
            }
        } else {
            indexRequest.setSendAllTm(true);
        }

        handleOneIndexResult(indexServer, indexRequest, observer, limit, next);
    }

    @Override
    public void listParameterIndex(Context ctx, ListParameterIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TmIndexService indexServer = getIndexService(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 20000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            indexRequest.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            indexRequest.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getGroupCount() > 0) {
            for (String group : request.getGroupList()) {
                indexRequest.getPpGroups().add(NamedObjectId.newBuilder().setName(group.trim()).build());
            }
        } else {
            indexRequest.setSendAllPp(true);
        }

        handleOneIndexResult(indexServer, indexRequest, observer, limit, next);
    }

    @Override
    public void listCompletenessIndex(Context ctx, ListCompletenessIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TmIndexService indexServer = getIndexService(instance);
        if (indexServer == null) {
            throw new BadRequestException("CCSDS Tm Index not enabled for instance '" + instance + "'");
        }

        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setSendCompletenessIndex(true);
        if (request.hasMergeTime()) {
            indexRequest.setMergeTime(request.getMergeTime());
        }

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            indexRequest.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            indexRequest.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        handleOneIndexResult(indexServer, indexRequest, observer, limit, next);
    }

    @Override
    public void streamPacketIndex(Context ctx, StreamPacketIndexRequest request, Observer<ArchiveRecord> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        IndexRequest indexRequest = new IndexRequest(instance);

        if (request.hasStart()) {
            indexRequest.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            indexRequest.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        for (String name : request.getNamesList()) {
            indexRequest.getTmPackets().add(NamedObjectId.newBuilder().setName(name).build());
        }
        indexRequest.setSendAllTm(request.getNamesCount() == 0);
        if (request.hasMergeTime()) {
            indexRequest.setMergeTime(request.getMergeTime());
        }
        streamArchiveRecords(null, indexRequest, observer);
    }

    @Override
    public void streamParameterIndex(Context ctx, StreamParameterIndexRequest request,
            Observer<ArchiveRecord> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setSendAllPp(true);
        if (request.hasMergeTime()) {
            indexRequest.setMergeTime(request.getMergeTime());
        }

        if (request.hasStart()) {
            indexRequest.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            indexRequest.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        streamArchiveRecords(null, indexRequest, observer);
    }

    @Override
    public void streamCommandIndex(Context ctx, StreamCommandIndexRequest request, Observer<ArchiveRecord> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setSendAllCmd(true);
        if (request.hasMergeTime()) {
            indexRequest.setMergeTime(request.getMergeTime());
        }

        if (request.hasStart()) {
            indexRequest.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            indexRequest.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        streamArchiveRecords(null, indexRequest, observer);
    }

    @Override
    public void streamEventIndex(Context ctx, StreamEventIndexRequest request, Observer<ArchiveRecord> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setSendAllEvent(true);
        if (request.hasMergeTime()) {
            indexRequest.setMergeTime(request.getMergeTime());
        }

        if (request.hasStart()) {
            indexRequest.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            indexRequest.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        streamArchiveRecords(null, indexRequest, observer);
    }

    @Override
    public void streamCompletenessIndex(Context ctx, StreamCompletenessIndexRequest request,
            Observer<ArchiveRecord> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TmIndexService indexServer = getIndexService(instance);
        if (indexServer == null) {
            throw new BadRequestException("Index service not enabled for instance '" + instance + "'");
        }

        IndexRequest indexRequest = new IndexRequest(instance);
        indexRequest.setSendCompletenessIndex(true);
        if (request.hasMergeTime()) {
            indexRequest.setMergeTime(request.getMergeTime());
        }

        if (request.hasStart()) {
            indexRequest.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            indexRequest.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        streamArchiveRecords(indexServer, indexRequest, observer);
    }

    @Override
    public void rebuildCcsdsIndex(Context ctx, RebuildCcsdsIndexRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        String instance = InstancesApi.verifyInstance(request.getInstance());
        TmIndexService indexer = getIndexService(instance);

        if (indexer instanceof CcsdsTmIndex) {
            CcsdsTmIndex ccsdsTmIndex = (CcsdsTmIndex) indexer;
            TimeInterval interval = new TimeInterval();
            if (request.hasStart()) {
                interval.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
            }
            if (request.hasStop()) {
                interval.setEnd(TimeEncoding.fromProtobufTimestamp(request.getStop()));
            }

            try {
                ccsdsTmIndex.rebuild(interval).whenComplete((r, t) -> {
                    if (t != null) {
                        observer.completeExceptionally(t);
                    } else {
                        observer.complete(Empty.getDefaultInstance());
                    }
                });
            } catch (YarchException e) {
                observer.completeExceptionally(e);
            }
        } else {
            observer.completeExceptionally(new BadRequestException("Not a CCSDS TM Index"));
        }
    }

    private TmIndexService getIndexService(String instance) throws HttpException {
        InstancesApi.verifyInstance(instance);
        YamcsServer yamcs = YamcsServer.getServer();
        return yamcs.getService(instance, TmIndexService.class);
    }

    /**
     * Submits an index request but returns only the first batch of results combined with a pagination token if the user
     * wishes to retrieve the next batch.
     * 
     * The batch size is determined by the IndexServer and is set to 500 (shared between all requested groups).
     */
    private void handleOneIndexResult(TmIndexService tmIndex, IndexRequest request, Observer<IndexResponse> observer,
            int limit, String token) throws HttpException {
        try {
            IndexResponse.Builder responseb = IndexResponse.newBuilder();
            Map<NamedObjectId, IndexGroup.Builder> groupBuilders = new HashMap<>();
            IndexRequestProcessor p = new IndexRequestProcessor(tmIndex, request, limit, token,
                    new IndexRequestListener() {

                        long last;

                        @Override
                        public void processData(ArchiveRecord rec) {
                            IndexGroup.Builder groupb = groupBuilders.get(rec.getId());
                            if (groupb == null) {
                                groupb = IndexGroup.newBuilder().setId(rec.getId());
                                groupBuilders.put(rec.getId(), groupb);
                            }
                            long first = TimeEncoding.fromProtobufTimestamp(rec.getFirst());
                            long last1 = TimeEncoding.fromProtobufTimestamp(rec.getLast());

                            IndexEntry.Builder ieb = IndexEntry.newBuilder()
                                    .setStart(TimeEncoding.toString(first))
                                    .setStop(TimeEncoding.toString(last1))
                                    .setCount(rec.getNum());
                            if (rec.hasSeqFirst()) {
                                ieb.setSeqStart(rec.getSeqFirst());
                            }
                            if (rec.hasSeqLast()) {
                                ieb.setSeqStop(rec.getSeqLast());
                            }
                            groupb.addEntry(ieb);
                            last = Math.max(last, last1);
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
                                observer.complete(responseb.build());
                            } else {
                                observer.completeExceptionally(
                                        new InternalServerErrorException("Failure while streaming index"));
                            }
                        }
                    });
            p.run();
        } catch (InvalidTokenException e) {
            observer.completeExceptionally(new BadRequestException("Invalid token specified"));
        }
    }

    private static void streamArchiveRecords(TmIndexService tmIndex, IndexRequest request,
            Observer<ArchiveRecord> observer) {
        IndexRequestProcessor p = new IndexRequestProcessor(tmIndex, request, -1, null,
                new IndexRequestListener() {
                    @Override
                    public void processData(ArchiveRecord record) {
                        observer.next(record);
                    }

                    @Override
                    public void finished(String token, boolean success) {
                        if (success) {
                            observer.complete();
                        } else {
                            observer.completeExceptionally(
                                    new InternalServerErrorException("Failure while streaming index"));
                        }
                    }
                });
        p.run();
    }
}
