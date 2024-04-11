package org.yamcs.http.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.http.api.ParameterRanger.Range;
import org.yamcs.logging.Log;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.parameterarchive.BackFillerListener;
import org.yamcs.parameterarchive.ConsumerAbortException;
import org.yamcs.parameterarchive.MultiParameterRetrieval;
import org.yamcs.parameterarchive.MultipleParameterRequest;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterId;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterIdValueList;
import org.yamcs.parameterarchive.ParameterInfoRetrieval;
import org.yamcs.parameterarchive.ParameterRequest;
import org.yamcs.protobuf.AbstractParameterArchiveApi;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.ArchivedParameterGroupResponse;
import org.yamcs.protobuf.ArchivedParameterInfo;
import org.yamcs.protobuf.ArchivedParameterSegmentsResponse;
import org.yamcs.protobuf.ArchivedParametersInfoResponse;
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
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.MutableLong;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.mdb.Mdb;

import com.google.protobuf.Empty;

public class ParameterArchiveApi extends AbstractParameterArchiveApi<Context> {

    private static final Log log = new Log(ParameterArchiveApi.class);
    private static final String DEFAULT_PROCESSOR = "realtime";

    private StreamArchiveApi streamArchiveApi = new StreamArchiveApi();

    @Override
    public void rebuildRange(Context ctx, RebuildRangeRequest request, Observer<Empty> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        if (!request.hasStart()) {
            throw new BadRequestException("no start specified");
        }
        if (!request.hasStop()) {
            throw new BadRequestException("no stop specified");
        }

        long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());

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
        if (request.hasSource() && isReplayAsked(request.getSource())) {
            streamArchiveApi.getParameterSamples(ctx, request, observer);
            return;
        }

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

        int sampleCount = request.hasCount() ? request.getCount() : 500;
        boolean useRawValue = request.hasUseRawValue() && request.getUseRawValue();

        Downsampler sampler = new Downsampler(start, stop, sampleCount);
        sampler.setUseRawValue(useRawValue);
        sampler.setGapTime(request.hasGapTime() ? request.getGapTime() : 120000);

        ParameterArchive parchive = getParameterArchive(ysi);

        ParameterCache pcache = null;
        if (!request.getNorealtime()) {
            String processorName = request.hasProcessor() ? request.getProcessor() : DEFAULT_PROCESSOR;
            Processor processor = ysi.getProcessor(processorName);
            pcache = processor.getParameterCache();
        }

        ParameterRequest pr = new ParameterRequest(start, stop, true, !useRawValue, useRawValue, true);
        SingleParameterRetriever spdr = new SingleParameterRetriever(parchive, pcache, pid, pr);
        try {
            spdr.retrieve(sampler);
        } catch (IOException e) {
            log.warn("Received exception during parameter retrieval", e);
            throw new InternalServerErrorException(e.toString());
        }

        TimeSeries.Builder series = TimeSeries.newBuilder();
        for (Sample s : sampler.collect()) {
            series.addSample(StreamArchiveApi.toGPBSample(s));
        }

        observer.complete(series.build());
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

        long minGap = request.hasMinGap() ? request.getMinGap() : 0;
        long maxGap = request.hasMaxGap() ? request.getMaxGap() : Long.MAX_VALUE;
        long minRange = request.hasMinRange() ? request.getMinRange() : -1;
        int maxValues = request.hasMaxValues() ? request.getMaxValues() : -1;

        ParameterArchive parchive = getParameterArchive(ysi);

        ParameterCache pcache = null;
        if (!request.getNorealtime()) {
            String processorName = request.hasProcessor() ? request.getProcessor() : DEFAULT_PROCESSOR;
            Processor processor = ysi.getProcessor(processorName);
            pcache = processor.getParameterCache();
        }

        ParameterRanger ranger = new ParameterRanger(minGap, maxGap, minRange, maxValues);

        ParameterRequest pr = new ParameterRequest(start, stop, true, true, false, true);
        SingleParameterRetriever spdr = new SingleParameterRetriever(parchive, pcache, pid, pr);
        try {
            spdr.retrieve(ranger);
        } catch (IOException e) {
            log.warn("Received exception during parameter retrieval ", e);
            throw new InternalServerErrorException(e.toString());
        }

        Ranges.Builder ranges = Ranges.newBuilder();
        for (Range r : ranger.getRanges()) {
            ranges.addRange(toGPBRange(r));
        }

        observer.complete(ranges.build());
    }

    @Override
    public void listParameterHistory(Context ctx, ListParameterHistoryRequest request,
            Observer<ListParameterHistoryResponse> observer) {
        if (request.hasSource() && isReplayAsked(request.getSource())) {
            streamArchiveApi.listParameterHistory(ctx, request, observer);
            return;
        }
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
        boolean ascending = request.getOrder().equals("asc");
        if (request.hasNext()) {
            TimeSortedPageToken token = TimeSortedPageToken.decode(request.getNext());
            if (ascending) {
                start = token.time;
            } else {
                stop = token.time;
            }
        }

        MultipleParameterRequest mpvr;
        ParameterArchive parchive = getParameterArchive(ysi);
        ParameterIdDb piddb = parchive.getParameterIdDb();
        String qn = requestedParamWithId.getQualifiedName();
        ParameterId[] pids = piddb.get(qn);
        if (pids != null) {
            mpvr = new MultipleParameterRequest(start, stop, pids, ascending);
        } else {
            log.debug("No parameter id found in the parameter archive for {}", qn);
            mpvr = null;
        }

        // do not use set limit because the data can be filtered down (e.g. noRepeat) and the limit applies the final
        // filtered data not to the input
        // one day the parameter archive will be smarter and do the filtering inside
        // mpvr.setLimit(limit);

        ParameterCache pcache = null;
        if (!request.getNorealtime()) {
            String processorName = request.hasProcessor() ? request.getProcessor() : DEFAULT_PROCESSOR;
            Processor processor = ysi.getProcessor(processorName);
            pcache = processor.getParameterCache();
        }

        ListParameterHistoryResponse.Builder resultb = ListParameterHistoryResponse.newBuilder();
        final int fLimit = limit + 1; // one extra to detect continuation token

        ParameterReplayListener replayListener = new ParameterReplayListener(0, fLimit) {
            @Override
            public void onParameterData(ParameterValueWithId pvwid) {
                if (resultb.getParameterCount() < fLimit - 1) {
                    resultb.addParameter(StreamArchiveApi.toGpb(pvwid, maxBytes));
                } else {
                    TimeSortedPageToken token = new TimeSortedPageToken(pvwid.getParameterValue().getGenerationTime());
                    resultb.setContinuationToken(token.encodeAsString());
                }
            }

            @Override
            public void replayFinished() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void replayFailed(Throwable t) {
                throw new UnsupportedOperationException();
            }
        };

        replayListener.setNoRepeat(request.getNorepeat());
        try {
            if (mpvr != null) {
                retrieveParameterData(parchive, pcache, requestedParamWithId, mpvr, replayListener);
            } else if (pcache != null) {
                // sendFromCache sends (start, stop) in ascending mode, so we have to make sure that start is also
                // included
                long _start = ascending ? start - 1 : start;
                sendFromCache(requestedParamWithId, pcache, ascending, _start, stop, replayListener);
            } else {
                log.warn("No parameter id found in the parameter archive for {} and parameter cache is not enabled",
                        qn);
                throw new NotFoundException();
            }
        } catch (DecodingException | RocksDBException | IOException e) {
            throw new InternalServerErrorException(e);
        }

        observer.complete(resultb.build());
    }

    private ParameterArchive getParameterArchive(YamcsServerInstance ysi) throws BadRequestException {
        List<ParameterArchive> l = ysi.getServices(ParameterArchive.class);

        if (l.isEmpty()) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }

        return l.get(0);
    }

    private void retrieveParameterData(ParameterArchive parchive, ParameterCache pcache, ParameterWithId pid,
            MultipleParameterRequest mpvr, ParameterReplayListener replayListener)
            throws RocksDBException, DecodingException, IOException {

        MutableLong lastParameterTime = new MutableLong(TimeEncoding.INVALID_INSTANT);
        Consumer<ParameterIdValueList> consumer = new Consumer<>() {
            boolean first = true;

            @Override
            public void accept(ParameterIdValueList pidvList) {
                lastParameterTime.setLong(pidvList.getValues().get(0).getGenerationTime());
                if (first && !mpvr.isAscending() && (pcache != null)) { // retrieve data from cache first
                    first = false;
                    sendFromCache(pid, pcache, false, lastParameterTime.getLong(), mpvr.getStop(), replayListener);
                }
                for (ParameterValue pv : pidvList.getValues()) {
                    replayListener.update(new ParameterValueWithId(pv, pid.getId()));
                    if (replayListener.isReplayAbortRequested()) {
                        throw new ConsumerAbortException();
                    }
                }
            }
        };
        MultiParameterRetrieval mpdr = new MultiParameterRetrieval(parchive, mpvr);
        mpdr.retrieve(consumer);

        // now add some data from cache
        if (pcache != null) {
            if (mpvr.isAscending()) {
                long start = (lastParameterTime.getLong() == TimeEncoding.INVALID_INSTANT) ? mpvr.getStart() - 1
                        : lastParameterTime.getLong();
                sendFromCache(pid, pcache, true, start, mpvr.getStop(), replayListener);
            } else if (lastParameterTime.getLong() == TimeEncoding.INVALID_INSTANT) {
                // no data retrieved from archive, but maybe there is still something in the cache to send
                sendFromCache(pid, pcache, false, mpvr.getStart(), mpvr.getStop(), replayListener);
            }
        }
    }

    // send data from cache with timestamps in (start, stop) if ascending or (start, stop] if descending interval
    private void sendFromCache(ParameterWithId pid, ParameterCache pcache, boolean ascending, long start,
            long stop, ParameterReplayListener replayListener) {
        List<ParameterValue> pvlist = pcache.getAllValues(pid.getParameter());

        if (pvlist == null) {
            return;
        }
        if (ascending) {
            int n = pvlist.size();
            for (int i = n - 1; i >= 0; i--) {
                ParameterValue pv = pvlist.get(i);
                if (pv.getGenerationTime() >= stop) {
                    break;
                }
                if (pv.getGenerationTime() > start) {
                    sendToListener(pv, pid, replayListener);
                    if (replayListener.isReplayAbortRequested()) {
                        break;
                    }
                }
            }
        } else {
            for (ParameterValue pv : pvlist) {
                if (pv.getGenerationTime() > stop) {
                    continue;
                }
                if (pv.getGenerationTime() <= start) {
                    break;
                }
                sendToListener(pv, pid, replayListener);
                if (replayListener.isReplayAbortRequested()) {
                    break;
                }
            }
        }
    }

    private void sendToListener(ParameterValue pv, ParameterWithId pid, ParameterReplayListener replayListener) {
        ParameterValue pv1;
        if (pid.getPath() != null) {
            try {
                pv1 = AggregateUtil.extractMember(pv, pid.getPath());
                if (pv1 == null) { // could be that we reference an element of an array that doesn't exist
                    return;
                }
            } catch (Exception e) {
                log.error("Failed to extract {} from parameter value {}", Arrays.toString(pid.getPath()), pv, e);
                return;
            }
        } else {
            pv1 = pv;
        }
        replayListener.update(new ParameterValueWithId(pv1, pid.getId()));
    }

    private boolean isReplayAsked(String source) throws HttpException {
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
        for (int i = 0; i < r.valueCount(); i++) {
            b.addEngValues(ValueUtility.toGbp(r.getValue(i)));
            b.addCounts(r.getCount(i));
        }

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

        ArchivedParametersInfoResponse.Builder respb = ArchivedParametersInfoResponse.newBuilder();
        int limit = request.hasLimit() ? request.getLimit() : 100;

        pdb.iterate((fqn, pid) -> {
            if (request.hasSystem() && !fqn.startsWith(request.getSystem())) {
                return true;
            }
            if (request.hasQ() && !fqn.contains(request.getQ())) {
                return true;
            }
            ArchivedParameterInfo.Builder apib = ArchivedParameterInfo.newBuilder().setFqn(fqn).setPid(pid.getPid());
            if (pid.getEngType() != null) {
                apib.setEngType(pid.getEngType());
            }
            if (pid.getRawType() != null) {
                apib.setRawType(pid.getRawType());
            }
            for (int gid : pgdb.getAllGroups(pid.getPid())) {
                apib.addGids(gid);
            }

            respb.addParameters(apib.build());
            return respb.getParametersCount() < limit;
        });

        observer.complete(respb.build());
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

        paraInfo.setFqn(paraId.getParamFqn());
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
                        .setFqn(fqn);
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

}
