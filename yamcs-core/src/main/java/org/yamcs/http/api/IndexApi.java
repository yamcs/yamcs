package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexRequestProcessor.InvalidTokenException;
import org.yamcs.archive.IndexServer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.protobuf.AbstractIndexApi;
import org.yamcs.protobuf.IndexEntry;
import org.yamcs.protobuf.IndexGroup;
import org.yamcs.protobuf.IndexResponse;
import org.yamcs.protobuf.ListCommandHistoryIndexRequest;
import org.yamcs.protobuf.ListCompletenessIndexRequest;
import org.yamcs.protobuf.ListEventIndexRequest;
import org.yamcs.protobuf.ListPacketIndexRequest;
import org.yamcs.protobuf.ListParameterIndexRequest;
import org.yamcs.protobuf.StreamCommandIndexRequest;
import org.yamcs.protobuf.StreamCompletenessIndexRequest;
import org.yamcs.protobuf.StreamEventIndexRequest;
import org.yamcs.protobuf.StreamIndexesRequest;
import org.yamcs.protobuf.StreamPacketIndexRequest;
import org.yamcs.protobuf.StreamParameterIndexRequest;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;

public class IndexApi extends AbstractIndexApi<Context> {

    @Override
    public void listCommandHistoryIndex(Context ctx, ListCommandHistoryIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getNameCount() > 0) {
            for (String name : request.getNameList()) {
                requestb.addCmdName(NamedObjectId.newBuilder().setName(name.trim()));
            }
        } else {
            requestb.setSendAllCmd(true);
        }

        handleOneIndexResult(indexServer, requestb.build(), observer, limit, next);
    }

    @Override
    public void listEventIndex(Context ctx, ListEventIndexRequest request, Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getSourceCount() > 0) {
            for (String source : request.getSourceList()) {
                requestb.addEventSource(NamedObjectId.newBuilder().setName(source.trim()));
            }
        } else {
            requestb.setSendAllEvent(true);
        }

        handleOneIndexResult(indexServer, requestb.build(), observer, limit, next);
    }

    @Override
    public void listPacketIndex(Context ctx, ListPacketIndexRequest request, Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getNameCount() > 0) {
            for (String name : request.getNameList()) {
                requestb.addTmPacket(NamedObjectId.newBuilder().setName(name.trim()));
            }
        } else {
            requestb.setSendAllTm(true);
        }

        handleOneIndexResult(indexServer, requestb.build(), observer, limit, next);
    }

    @Override
    public void listParameterIndex(Context ctx, ListParameterIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 20000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getGroupCount() > 0) {
            for (String group : request.getGroupList()) {
                requestb.addPpGroup(NamedObjectId.newBuilder().setName(group.trim()));
            }
        } else {
            requestb.setSendAllPp(true);
        }

        handleOneIndexResult(indexServer, requestb.build(), observer, limit, next);
    }

    @Override
    public void listCompletenessIndex(Context ctx, ListCompletenessIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setSendCompletenessIndex(true);
        requestb.setInstance(instance);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        handleOneIndexResult(indexServer, requestb.build(), observer, limit, next);
    }

    @Override
    public void streamIndexes(Context ctx, StreamIndexesRequest request, Observer<IndexResult> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);

        if (request.hasStart()) {
            requestb.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            requestb.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        for (String name : request.getPacketnamesList()) {
            requestb.addTmPacket(NamedObjectId.newBuilder().setName(name));
        }

        Set<String> filter = new HashSet<>();
        for (String name : request.getFiltersList()) {
            filter.add(name.toLowerCase());
        }

        if (filter.isEmpty() && requestb.getTmPacketCount() == 0) {
            requestb.setSendAllTm(true);
            requestb.setSendAllPp(true);
            requestb.setSendAllCmd(true);
            requestb.setSendAllEvent(true);
            requestb.setSendCompletenessIndex(true);
        } else {
            requestb.setSendAllTm(filter.contains("tm") && requestb.getTmPacketCount() == 0);
            requestb.setSendAllPp(filter.contains("pp"));
            requestb.setSendAllCmd(filter.contains("commands"));
            requestb.setSendAllEvent(filter.contains("events"));
            requestb.setSendCompletenessIndex(filter.contains("completeness"));
        }

        streamIndexResults(indexServer, requestb.build(), observer);
    }

    @Override
    public void streamPacketIndex(Context ctx, StreamPacketIndexRequest request, Observer<ArchiveRecord> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);

        if (request.hasStart()) {
            requestb.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            requestb.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        for (String name : request.getNamesList()) {
            requestb.addTmPacket(NamedObjectId.newBuilder().setName(name));
        }
        requestb.setSendAllTm(request.getNamesCount() == 0);
        streamArchiveRecords(indexServer, requestb.build(), observer);
    }

    @Override
    public void streamParameterIndex(Context ctx, StreamParameterIndexRequest request,
            Observer<ArchiveRecord> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setSendAllPp(true);

        if (request.hasStart()) {
            requestb.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            requestb.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        streamArchiveRecords(indexServer, requestb.build(), observer);
    }

    @Override
    public void streamCommandIndex(Context ctx, StreamCommandIndexRequest request, Observer<ArchiveRecord> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setSendAllCmd(true);

        if (request.hasStart()) {
            requestb.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            requestb.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        streamArchiveRecords(indexServer, requestb.build(), observer);
    }

    @Override
    public void streamEventIndex(Context ctx, StreamEventIndexRequest request, Observer<ArchiveRecord> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setSendAllEvent(true);

        if (request.hasStart()) {
            requestb.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            requestb.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        streamArchiveRecords(indexServer, requestb.build(), observer);
    }

    @Override
    public void streamCompletenessIndex(Context ctx, StreamCompletenessIndexRequest request,
            Observer<ArchiveRecord> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setSendCompletenessIndex(true);

        if (request.hasStart()) {
            requestb.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            requestb.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }

        streamArchiveRecords(indexServer, requestb.build(), observer);
    }

    private IndexServer verifyIndexServer(String instance) throws HttpException {
        RestHandler.verifyInstance(instance);
        YamcsServer yamcs = YamcsServer.getServer();
        List<IndexServer> services = yamcs.getServices(instance, IndexServer.class);
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
    private void handleOneIndexResult(IndexServer indexServer, IndexRequest request, Observer<IndexResponse> observer,
            int limit, String token) throws HttpException {
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
        } catch (InvalidTokenException e) {
            observer.completeExceptionally(new BadRequestException("Invalid token specified"));
        } catch (YamcsException e) {
            observer.completeExceptionally(e);
        }
    }

    private static void streamIndexResults(IndexServer indexServer, IndexRequest request,
            Observer<IndexResult> observer) {
        try {
            indexServer.submitIndexRequest(request, new IndexRequestListener() {

                private IndexResult.Builder indexResult;

                @Override
                public void begin(IndexType type, String tblName) {
                    if (indexResult != null) {
                        observer.next(indexResult.build());
                    }
                    indexResult = newBuilder(type.name(), tblName);
                }

                @Override
                public void processData(ArchiveRecord record) {
                    indexResult.addRecords(record);
                    if (indexResult.getRecordsCount() > 500) {
                        observer.next(indexResult.build());
                        indexResult = newBuilder(indexResult.getType(), indexResult.getTableName());
                    }
                }

                @Override
                public void finished(String token, boolean success) {
                    if (success) {
                        if (indexResult != null && indexResult.getRecordsCount() > 0) {
                            observer.next(indexResult.build());
                        }
                        observer.complete();
                    } else {
                        observer.completeExceptionally(
                                new InternalServerErrorException("Failure while streaming index"));
                    }
                }

                private IndexResult.Builder newBuilder(String type, String tblName) {
                    IndexResult.Builder b = IndexResult.newBuilder().setType(type);
                    if (tblName != null) {
                        b.setTableName(tblName);
                    }
                    return b;
                }
            });
        } catch (YamcsException e) {
            observer.completeExceptionally(e);
        }
    }

    private static void streamArchiveRecords(IndexServer indexServer, IndexRequest request,
            Observer<ArchiveRecord> observer) {
        try {
            indexServer.submitIndexRequest(request, new IndexRequestListener() {

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
        } catch (YamcsException e) {
            observer.completeExceptionally(e);
        }
    }
}
