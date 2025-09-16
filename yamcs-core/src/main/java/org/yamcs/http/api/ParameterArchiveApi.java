package org.yamcs.http.api;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.rocksdb.RocksDBException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.AbstractPaginatedParameterRetrievalConsumer.PaginatedSingleParameterRetrievalConsumer;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.http.api.ParameterRanger.Range;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameter.ParameterRetrievalService;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.parameterarchive.BackFiller;
import org.yamcs.parameterarchive.BackFillerListener;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterId;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterInfoRetrieval;
import org.yamcs.protobuf.AbstractParameterArchiveApi;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.ArchivedParameterGroupResponse;
import org.yamcs.protobuf.ArchivedParameterInfo;
import org.yamcs.protobuf.ArchivedParameterSegmentsResponse;
import org.yamcs.protobuf.ArchivedParametersInfoResponse;
import org.yamcs.protobuf.DisableBackfillingRequest;
import org.yamcs.protobuf.EnableBackfillingRequest;
import org.yamcs.protobuf.GetArchivedParameterGroupRequest;
import org.yamcs.protobuf.GetArchivedParameterSegmentsRequest;
import org.yamcs.protobuf.GetArchivedParametersInfoRequest;
import org.yamcs.protobuf.GetParameterRangesRequest;
import org.yamcs.protobuf.PurgeRequest;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.RebuildRangeRequest;
import org.yamcs.protobuf.SubscribeBackfillingData;
import org.yamcs.protobuf.SubscribeBackfillingData.BackfillFinishedInfo;
import org.yamcs.protobuf.SubscribeBackfillingRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;

import com.google.gson.Gson;
import com.google.protobuf.Empty;

public class ParameterArchiveApi extends AbstractParameterArchiveApi<Context> {

    private static final Log log = new Log(ParameterArchiveApi.class);

    @Override
    public void rebuildRange(Context ctx, RebuildRangeRequest request, Observer<Empty> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        long start = TimeEncoding.INVALID_INSTANT;
        long stop = TimeEncoding.INVALID_INSTANT;

        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        ParameterArchive parchive = getParameterArchive(ysi);
        try {
            parchive.reprocess(start, stop);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void subscribeBackfilling(Context ctx, SubscribeBackfillingRequest request,
            Observer<SubscribeBackfillingData> observer) {
        var ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        var parchives = ysi.getServices(ParameterArchive.class);
        if (parchives.isEmpty()) {
            // Ignore quietely
            return;
        }

        var parchive = parchives.get(0);
        var backFiller = parchive.getBackFiller();
        if (backFiller == null) {
            // Ignore quietely
            return;
        }

        var pendingNotifications = new ConcurrentLinkedQueue<BackfillFinishedInfo>();

        var listener = (BackFillerListener) (start, stop, processedParameters) -> {
            pendingNotifications.add(BackfillFinishedInfo.newBuilder()
                    .setStart(TimeEncoding.toProtobufTimestamp(start))
                    .setStop(TimeEncoding.toProtobufTimestamp(stop))
                    .setProcessedParameters(processedParameters)
                    .build());
        };

        var exec = YamcsServer.getServer().getThreadPoolExecutor();
        var execFuture = exec.scheduleAtFixedRate(() -> {
            if (!pendingNotifications.isEmpty()) {
                var b = SubscribeBackfillingData.newBuilder();

                BackfillFinishedInfo item;
                while ((item = pendingNotifications.poll()) != null) {
                    b.addFinished(item);
                }

                observer.next(b.build());
            }
        }, 0, 5, TimeUnit.SECONDS);

        observer.setCancelHandler(() -> {
            backFiller.removeListener(listener);
            execFuture.cancel(false);
        });
        backFiller.addListener(listener);
    }

    @Override
    public void getParameterSamples(Context ctx, GetParameterSamplesRequest request,
            Observer<TimeSeries> observer) {

        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(ysi.getName());

        ParameterWithId pid = MdbApi.verifyParameterWithId(ctx, mdb, request.getName());

        /*
         * TODO check commented out, in order to support sampling system parameters which don't have a type
         * 
         * ParameterType ptype = p.getParameterType(); if (ptype == null) { throw new
         * BadRequestException("Requested parameter has no type"); } else if (!(ptype instanceof FloatParameterType) &&
         * !(ptype instanceof IntegerParameterType)) { throw new
         * BadRequestException("Only integer or float parameters can be sampled. Got " + ptype.getTypeAsString()); }
         */

        long defaultStop = TimeEncoding.getWallclockTime();
        long defaultStart = defaultStop - (1000 * 60 * 60); // 1 hour

        long start = defaultStart;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = defaultStop;
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        if (start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        int sampleCount = request.hasCount() ? request.getCount() : 500;
        boolean useRawValue = request.hasUseRawValue() && request.getUseRawValue();

        Downsampler sampler = new Downsampler(start, stop, sampleCount);
        sampler.setUseRawValue(useRawValue);
        sampler.setGapTime(request.hasGapTime() ? request.getGapTime() : 120000);

        ParameterRetrievalService prs = getParameterRetrievalService(ysi);
        ParameterRetrievalOptions opts = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withAscending(true)
                .withRetrieveRawValues(useRawValue)
                .withRetrieveEngineeringValues(!useRawValue)
                .withoutRealtime(request.getNorealtime())
                .withoutParchive(request.hasSource() && isReplayAsked(request.getSource()))
                .build();
        prs.retrieveScalar(pid, opts, sampler)
                .thenRun(() -> {
                    TimeSeries.Builder series = TimeSeries.newBuilder();
                    for (Sample s : sampler.collect()) {
                        series.addSample(StreamArchiveApi.toGPBSample(s));
                    }
                    observer.complete(series.build());
                })
                .exceptionally(e -> {
                    log.warn("Received exception during parameter retrieval", e);
                    observer.completeExceptionally(new InternalServerErrorException(e.toString()));
                    return null;
                });

    }

    @Override
    public void getParameterRanges(Context ctx, GetParameterRangesRequest request, Observer<Ranges> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(ysi.getName());

        ParameterWithId pid = MdbApi.verifyParameterWithId(ctx, mdb, request.getName());

        long start = 0;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = TimeEncoding.getWallclockTime();
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        if (start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        long minGap = request.hasMinGap() ? request.getMinGap() : 0;
        long maxGap = request.hasMaxGap() ? request.getMaxGap() : Long.MAX_VALUE;
        long minRange = request.hasMinRange() ? request.getMinRange() : -1;
        int maxValues = request.hasMaxValues() ? request.getMaxValues() : -1;

        ParameterRanger ranger = new ParameterRanger(minGap, maxGap, minRange, maxValues);

        ParameterRetrievalService prs = getParameterRetrievalService(ysi);
        ParameterRetrievalOptions opts = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withRetrieveRawValues(false)
                .withoutRealtime(request.getNorealtime())
                .build();

        prs.retrieveScalar(pid, opts, ranger)
                .thenRun(() -> {
                    Ranges.Builder ranges = Ranges.newBuilder();
                    for (Range r : ranger.getRanges()) {
                        ranges.addRange(toGPBRange(r));
                    }
                    observer.complete(ranges.build());
                })
                .exceptionally(e -> {
                    log.warn("Received exception during parameter retrieval", e);
                    observer.completeExceptionally(new InternalServerErrorException(e.toString()));
                    return null;
                });

    }

    @Override
    public void listParameterHistory(Context ctx, ListParameterHistoryRequest request,
            Observer<ListParameterHistoryResponse> observer) {

        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(ysi.getName());
        ParameterWithId requestedParamWithId = MdbApi.verifyParameterWithId(ctx, mdb, request.getName());

        int limit = request.hasLimit() ? request.getLimit() : 100;
        int maxBytes = request.hasMaxBytes() ? request.getMaxBytes() : -1;

        long start = 0;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = TimeEncoding.getWallclockTime();
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        if (start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        boolean ascending = request.getOrder().equals("asc");
        if (request.hasNext()) {
            TimeSortedPageToken token = TimeSortedPageToken.decode(request.getNext());
            if (ascending) {
                start = token.time;
            } else {
                stop = token.time;
            }
        }
        var optsb = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withAscending(ascending)
                .withRetrieveParameterStatus(false);

        if (request.hasSource() && isReplayAsked(request.getSource())) {
            optsb = optsb
                    .withoutParchive(true)
                    .withoutReplay(false);
        } else {
            if (request.hasNoreplay()) {
                optsb = optsb.withoutReplay(request.getNoreplay());
            }
            optsb = optsb.withoutRealtime(request.getNorealtime());
        }

        ParameterRetrievalOptions opts = optsb.build();
        ParameterRetrievalService prs = getParameterRetrievalService(ysi);

        ListParameterHistoryResponse.Builder resultb = ListParameterHistoryResponse.newBuilder();
        final int fLimit = limit + 1; // one extra to detect continuation token

        PaginatedSingleParameterRetrievalConsumer replayListener = new PaginatedSingleParameterRetrievalConsumer(0,
                fLimit) {
            @Override
            public void onParameterData(ParameterValueWithId pvwid) {
                if (resultb.getParameterCount() < fLimit - 1) {
                    resultb.addParameter(StreamArchiveApi.toGpb(pvwid, maxBytes));
                } else {
                    TimeSortedPageToken token = new TimeSortedPageToken(pvwid.getParameterValue().getGenerationTime());
                    resultb.setContinuationToken(token.encodeAsString());
                }
            }
        };

        replayListener.setNoRepeat(request.getNorepeat());
        prs.retrieveSingle(requestedParamWithId, opts, replayListener)
                .thenRun(() -> {
                    observer.complete(resultb.build());
                })
                .exceptionally(e -> {
                    log.warn("Received exception during parameter retrieval", e);
                    observer.completeExceptionally(new InternalServerErrorException(e.toString()));
                    return null;
                });
    }

    private ParameterArchive getParameterArchive(YamcsServerInstance ysi) throws BadRequestException {
        List<ParameterArchive> l = ysi.getServices(ParameterArchive.class);

        if (l.isEmpty()) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }

        return l.get(0);
    }

    static ParameterRetrievalService getParameterRetrievalService(YamcsServerInstance ysi) throws BadRequestException {
        List<ParameterRetrievalService> l = ysi.getServices(ParameterRetrievalService.class);

        if (l.isEmpty()) {
            throw new BadRequestException("ParameterRetrievalService not configured for this instance");
        }

        return l.get(0);
    }

    static boolean isReplayAsked(String source) throws HttpException {
        if (source.equalsIgnoreCase("ParameterArchive")) {
            return false;
        } else if (source.equalsIgnoreCase("replay")) {
            return true;
        } else {
            throw new BadRequestException(
                    "Bad value for parameter 'source'; valid values are: 'ParameterArchive' or 'replay'");
        }
    }

    private static Ranges.Range toGPBRange(Range r) {
        Ranges.Range.Builder b = Ranges.Range.newBuilder();
        b.setCount(r.totalCount());
        b.setStart(TimeEncoding.toProtobufTimestamp(r.start));
        b.setStop(TimeEncoding.toProtobufTimestamp(r.stop));
        var valueCount = 0;
        for (int i = 0; i < r.valueCount(); i++) {
            b.addEngValues(ValueUtility.toGbp(r.getValue(i)));
            b.addCounts(r.getCount(i));
            valueCount += r.getCount(i);
        }
        b.setOtherCount(r.totalCount() - valueCount);

        return b.build();
    }

    @Override
    public void getArchivedParametersInfo(Context ctx, GetArchivedParametersInfoRequest request,
            Observer<ArchivedParametersInfoResponse> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        ParameterArchive parchive = getParameterArchive(ysi);
        ParameterIdDb pdb = parchive.getParameterIdDb();
        ParameterGroupIdDb pgdb = parchive.getParameterGroupIdDb();

        var responseb = ArchivedParametersInfoResponse.newBuilder();
        int limit = request.hasLimit() ? request.getLimit() : 100;

        var filter = request.hasFilter()
                ? ArchivedParameterFilterFactory.create(request.getFilter())
                : null;

        var nextToken = request.hasNext() ? PidPageToken.decode(request.getNext()) : null;

        pdb.iterate((fqn, pid) -> {
            if (nextToken != null && pid.getPid() <= nextToken.pid) {
                return true; // Continue
            }
            if (!pid.isSimple()) {
                return true; // Continue;
            }

            var infob = ArchivedParameterInfo.newBuilder()
                    .setParameter(fqn)
                    .setPid(pid.getPid());
            if (pid.getEngType() != null) {
                infob.setEngType(pid.getEngType());
            }
            if (pid.getRawType() != null) {
                infob.setRawType(pid.getRawType());
            }
            for (int gid : pgdb.getAllGroups(pid.getPid())) {
                infob.addGids(gid);
            }

            var info = infob.build();

            if (filter != null && !filter.matches(info)) {
                return true; // Continue
            }

            responseb.addPids(info);
            return responseb.getPidsCount() < limit;
        });

        if (responseb.getPidsCount() > 0) {
            var lastPid = responseb.getPids(responseb.getPidsCount() - 1).getPid();
            var token = new PidPageToken(lastPid);
            responseb.setContinuationToken(token.encodeAsString());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getArchivedParameterSegments(Context ctx, GetArchivedParameterSegmentsRequest request,
            Observer<ArchivedParameterSegmentsResponse> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        if (!request.hasPid()) {
            throw new BadRequestException("id is mandatory");
        }
        int pid = request.getPid();

        long start = 0;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = ysi.getTimeService().getMissionTime();
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        ParameterArchive parchive = getParameterArchive(ysi);
        ParameterIdDb pdb = parchive.getParameterIdDb();

        ArchivedParameterSegmentsResponse.Builder resp = ArchivedParameterSegmentsResponse.newBuilder();
        ArchivedParameterInfo.Builder paraInfo = ArchivedParameterInfo.newBuilder();

        ParameterId paraId = pdb.getParameterId(pid);

        if (paraId == null) {
            throw new NotFoundException("Unknown parameter id " + pid);
        }

        paraInfo.setParameter(paraId.getParamFqn());
        paraInfo.setEngType(paraId.getEngType());
        paraInfo.setRawType(paraId.getRawType());
        paraInfo.setPid(pid);

        resp.setParameterInfo(paraInfo.build());

        ParameterInfoRetrieval pir = new ParameterInfoRetrieval(parchive, paraId, start, stop);

        try {
            pir.retrieve(segInfo -> resp.addSegments(segInfo));
            observer.complete(resp.build());
        } catch (RocksDBException | IOException e) {
            log.error("Error retrieving parameter info", e);
            throw new InternalServerErrorException(e.toString());
        }
    }

    @Override
    public void getArchivedParameterGroup(Context ctx, GetArchivedParameterGroupRequest request,
            Observer<ArchivedParameterGroupResponse> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);
        if (!request.hasGid()) {
            throw new BadRequestException("gid is mandatory");
        }
        int gid = request.getGid();

        ParameterArchive parchive = getParameterArchive(ysi);
        ParameterIdDb pdb = parchive.getParameterIdDb();
        ParameterGroupIdDb pgdb = parchive.getParameterGroupIdDb();
        IntArray pids;

        try {
            pids = pgdb.getParameterGroup(gid);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("No such group " + gid);
        }
        SortedIntArray sortedPids = new SortedIntArray(pids);
        ArchivedParameterGroupResponse.Builder resp = ArchivedParameterGroupResponse.newBuilder();

        pdb.iterate((fqn, paraId) -> {
            if (sortedPids.contains(paraId.getPid())) {
                ArchivedParameterInfo.Builder paraInfo = ArchivedParameterInfo.newBuilder()
                        .setParameter(fqn);
                if (paraId.getEngType() != null) {
                    paraInfo.setEngType(paraId.getEngType());
                }
                if (paraId.getRawType() != null) {
                    paraInfo.setRawType(paraId.getRawType());
                }

                paraInfo.setPid(paraId.getPid());

                resp.addParameters(paraInfo.build());
            }
            return true;
        });
        observer.complete(resp.build());
    }

    @Override
    public void purge(Context ctx, PurgeRequest request, Observer<Empty> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        ParameterArchive parchive = getParameterArchive(ysi);
        try {
            parchive.purge();
        } catch (RocksDBException | InterruptedException | IOException e) {
            log.error("Error purging parameter archive", e);
            throw new InternalServerErrorException(e.toString());
        }

        observer.complete(Empty.getDefaultInstance());

    }

    @Override
    public void disableBackfilling(Context ctx, DisableBackfillingRequest request, Observer<Empty> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        getBackFiller(ysi).enableAutomaticBackfilling(false);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void enableBackfilling(Context ctx, EnableBackfillingRequest request, Observer<Empty> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        getBackFiller(ysi).enableAutomaticBackfilling(true);
        observer.complete(Empty.getDefaultInstance());
    }

    BackFiller getBackFiller(YamcsServerInstance ysi) {
        var parchive = getParameterArchive(ysi);
        var backfiller = parchive.getBackFiller();
        if (backfiller == null) {
            throw new BadRequestException("Backfiller not enabled");
        }
        return backfiller;
    }

    private static class PidPageToken {

        /**
         * PID associated with the last object that was emitted.
         */
        public int pid;

        public PidPageToken(int pid) {
            this.pid = pid;
        }

        public static PidPageToken decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded));
            return new Gson().fromJson(decoded, PidPageToken.class);
        }

        public String encodeAsString() {
            String json = new Gson().toJson(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        }
    }
}
