package org.yamcs.http.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.http.api.ParameterRanger.Range;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.parameterarchive.ConsumerAbortException;
import org.yamcs.parameterarchive.MultiParameterDataRetrieval;
import org.yamcs.parameterarchive.MultipleParameterValueRequest;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterArchive.Partition;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterId;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterIdValueList;
import org.yamcs.parameterarchive.ParameterRequest;
import org.yamcs.protobuf.AbstractParameterArchiveApi;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.DeletePartitionsRequest;
import org.yamcs.protobuf.GetArchivedParameterInfoRequest;
import org.yamcs.protobuf.GetParameterRangesRequest;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.RebuildRangeRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.MutableLong;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.Empty;

public class ParameterArchiveApi extends AbstractParameterArchiveApi<Context> {

    private static final Log log = new Log(ParameterArchiveApi.class);
    private static final String DEFAULT_PROCESSOR = "realtime";

    private StreamArchiveApi streamArchiveApi = new StreamArchiveApi();

    @Override
    public void rebuildRange(Context ctx, RebuildRangeRequest request, Observer<Empty> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        if (!request.hasStart()) {
            throw new BadRequestException("no start specified");
        }
        if (!request.hasStop()) {
            throw new BadRequestException("no stop specified");
        }

        long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());

        ParameterArchive parchive = getParameterArchive(instance);
        try {
            parchive.reprocess(start, stop);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void deletePartitions(Context ctx, DeletePartitionsRequest request,
            Observer<StringMessage> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        if (!request.hasStart()) {
            throw new BadRequestException("no start specified");
        }
        if (!request.hasStop()) {
            throw new BadRequestException("no stop specified");
        }

        long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());

        ParameterArchive parchive = getParameterArchive(instance);
        try {
            List<Partition> removed = parchive.deletePartitions(start, stop);
            StringBuilder sb = new StringBuilder();
            sb.append("removed the following partitions: ");
            boolean first = true;
            for (Partition p : removed) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(p.toString());
            }

            StringMessage sm = StringMessage.newBuilder().setMessage(sb.toString()).build();
            observer.complete(sm);

        } catch (RocksDBException e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public void getArchivedParameterInfo(Context ctx, GetArchivedParameterInfoRequest request,
            Observer<StringMessage> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ControlArchiving);

        String fqn = request.getName();
        ParameterArchive parchive = getParameterArchive(instance);
        ParameterIdDb pdb = parchive.getParameterIdDb();
        ParameterId[] pids = pdb.get(fqn);
        StringMessage sm = StringMessage.newBuilder().setMessage(Arrays.toString(pids)).build();
        observer.complete(sm);
    }

    @Override
    public void getParameterSamples(Context ctx, GetParameterSamplesRequest request,
            Observer<TimeSeries> observer) {
        if (request.hasSource() && isReplayAsked(request.getSource())) {
            streamArchiveApi.getParameterSamples(ctx, request, observer);
            return;
        }

        String instance = ManagementApi.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);

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

        Downsampler sampler = new Downsampler(start, stop, sampleCount);
        ParameterArchive parchive = getParameterArchive(instance);

        ParameterCache pcache = null;
        if (!request.getNorealtime()) {
            String processorName = request.hasProcessor() ? request.getProcessor() : DEFAULT_PROCESSOR;
            Processor processor = Processor.getInstance(instance, processorName);
            pcache = processor.getParameterCache();
        }

        ParameterRequest pr = new ParameterRequest(start, stop, true, true, false, false);
        SingleParameterRetriever spdr = new SingleParameterRetriever(parchive, pcache, pid, pr);
        try {
            spdr.retrieve(sampler);
        } catch (IOException e) {
            log.warn("Received exception during parameter retrieval", e);
            throw new InternalServerErrorException(e.getMessage());
        }

        TimeSeries.Builder series = TimeSeries.newBuilder();
        for (Sample s : sampler.collect()) {
            series.addSample(StreamArchiveApi.toGPBSample(s));
        }

        observer.complete(series.build());
    }

    @Override
    public void getParameterRanges(Context ctx, GetParameterRangesRequest request, Observer<Ranges> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);

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

        ParameterArchive parchive = getParameterArchive(instance);

        ParameterCache pcache = null;
        if (!request.getNorealtime()) {
            String processorName = request.hasProcessor() ? request.getProcessor() : DEFAULT_PROCESSOR;
            Processor processor = Processor.getInstance(instance, processorName);
            pcache = processor.getParameterCache();
        }

        ParameterRanger ranger = new ParameterRanger(minGap, maxGap);

        ParameterRequest pr = new ParameterRequest(start, stop, true, true, false, true);
        SingleParameterRetriever spdr = new SingleParameterRetriever(parchive, pcache, pid, pr);
        try {
            spdr.retrieve(ranger);
        } catch (IOException e) {
            log.warn("Received exception during parameter retrieval ", e);
            throw new InternalServerErrorException(e.getMessage());
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

        String instance = ManagementApi.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        ParameterWithId requestedParamWithId = MdbApi.verifyParameterWithId(ctx, mdb, request.getName());

        NamedObjectId requestedId = requestedParamWithId.getId();

        int limit = request.hasLimit() ? request.getLimit() : 100;

        long start = 0;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = TimeEncoding.getWallclockTime();
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        boolean ascending = request.getOrder().equals("asc");

        ParameterArchive parchive = getParameterArchive(instance);
        ParameterIdDb piddb = parchive.getParameterIdDb();
        IntArray pidArray = new IntArray();
        IntArray pgidArray = new IntArray();
        String qn = requestedParamWithId.getQualifiedName();
        ParameterId[] pids = piddb.get(qn);

        BitSet retrieveRawValues = new BitSet();
        if (pids != null) {
            ParameterGroupIdDb pgidDb = parchive.getParameterGroupIdDb();
            for (ParameterId pid : pids) {
                int[] pgids = pgidDb.getAllGroups(pid.pid);
                for (int pgid : pgids) {
                    if (pid.getRawType() != null) {
                        retrieveRawValues.set(pidArray.size());
                    }
                    pidArray.add(pid.pid);
                    pgidArray.add(pgid);
                }
            }

            if (pidArray.isEmpty()) {
                log.error("No parameter group id found in the parameter archive for {}", qn);
                throw new NotFoundException();
            }
        } else {
            log.warn("No parameter id found in the parameter archive for {}", qn);
        }
        String[] pnames = new String[pidArray.size()];
        Arrays.fill(pnames, requestedParamWithId.getQualifiedName());
        MultipleParameterValueRequest mpvr = new MultipleParameterValueRequest(start, stop, pnames, pidArray.toArray(),
                pgidArray.toArray(), retrieveRawValues, ascending);
        // do not use set limit because the data can be filtered down (e.g. noRepeat) and the limit applies the final
        // filtered data not to the input
        // one day the parameter archive will be smarter and do the filtering inside
        // mpvr.setLimit(limit);

        ParameterCache pcache = null;
        if (!request.getNorealtime()) {
            String processorName = request.hasProcessor() ? request.getProcessor() : DEFAULT_PROCESSOR;
            Processor processor = Processor.getInstance(instance, processorName);
            pcache = processor.getParameterCache();
        }

        ListParameterHistoryResponse.Builder resultb = ListParameterHistoryResponse.newBuilder();
        final int fLimit = limit + 1; // one extra to detect continuation token

        ParameterReplayListener replayListener = new ParameterReplayListener(0, fLimit) {

            @Override
            public void onParameterData(ParameterValueWithId pvwid) {
                if (resultb.getParameterCount() < fLimit - 1) {
                    resultb.addParameter(pvwid.toGbpParameterValue());
                } else {
                    Pvalue.ParameterValue last = resultb.getParameter(resultb.getParameterCount() - 1);
                    TimeSortedPageToken token = new TimeSortedPageToken(last.getGenerationTime());
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
            // FIXME - make async
            retrieveParameterData(parchive, pcache, requestedParamWithId, mpvr, replayListener);
        } catch (DecodingException | RocksDBException | IOException e) {
            throw new InternalServerErrorException(e);
        }

        observer.complete(resultb.build());
    }

    private ParameterArchive getParameterArchive(String instance) throws BadRequestException {
        YamcsServer yamcs = YamcsServer.getServer();
        List<ParameterArchive> l = yamcs.getServices(instance, ParameterArchive.class);

        if (l.isEmpty()) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }

        return l.get(0);
    }

    private void retrieveParameterData(ParameterArchive parchive, ParameterCache pcache, ParameterWithId pid,
            MultipleParameterValueRequest mpvr, ParameterReplayListener replayListener)
            throws RocksDBException, DecodingException, IOException {

        MutableLong lastParameterTime = new MutableLong(TimeEncoding.INVALID_INSTANT);
        Consumer<ParameterIdValueList> consumer = new Consumer<ParameterIdValueList>() {
            boolean first = true;

            @Override
            public void accept(ParameterIdValueList pidvList) {
                lastParameterTime.setLong(pidvList.getValues().get(0).getGenerationTime());
                if (first && !mpvr.isAscending() && (pcache != null)) { // retrieve data from cache first
                    first = false;
                    sendFromCache(pid, pcache, false, lastParameterTime.getLong(), mpvr.getStop(), replayListener);
                }
                ParameterValue pv = pidvList.getValues().get(0);
                replayListener.update(new ParameterValueWithId(pv, pid.getId()));
                if (replayListener.isReplayAbortRequested()) {
                    throw new ConsumerAbortException();
                }
            }
        };
        MultiParameterDataRetrieval mpdr = new MultiParameterDataRetrieval(parchive, mpvr);
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
        b.setTimeStart(TimeEncoding.toString(r.start));
        b.setTimeStop(TimeEncoding.toString(r.stop));
        b.setEngValue(ValueUtility.toGbp(r.v));
        b.setCount(r.count);
        return b.build();
    }
}
