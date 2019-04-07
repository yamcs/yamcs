package org.yamcs.web.rest.archive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.api.MediaType;
import org.yamcs.archive.ParameterRecorder;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.parameterarchive.ConsumerAbortException;
import org.yamcs.parameterarchive.MultiParameterDataRetrieval;
import org.yamcs.parameterarchive.MultipleParameterValueRequest;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterId;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterIdValueList;
import org.yamcs.parameterarchive.ParameterRequest;
import org.yamcs.protobuf.Archive.ParameterGroupInfo;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Rest.ListParameterValuesResponse;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.MutableLong;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestParameterReplayListener;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.archive.ParameterRanger.Range;
import org.yamcs.web.rest.archive.RestDownsampler.Sample;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.collect.BiMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

/**
 * Provides parameters from ParameterArchive or via replays using {@link ArchiveParameterReplayRestHandler}
 * 
 * @author nm
 *
 */
public class ArchiveParameterRestHandler extends RestHandler {
    private static final String DEFAULT_PROCESSOR = "realtime";
    private static final Logger log = LoggerFactory.getLogger(ArchiveParameterRestHandler.class);
    private ArchiveParameterReplayRestHandler aprh = new ArchiveParameterReplayRestHandler();

    @Route(path = "/api/archive/:instance/parameter-groups", method = "GET")
    public void listGroups(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ParameterGroupInfo.Builder responseb = ParameterGroupInfo.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(ParameterRecorder.TABLE_NAME);
        BiMap<String, Short> enumValues = tableDefinition.getEnumValues("group");
        if (enumValues != null) {
            List<String> unsortedGroups = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                unsortedGroups.add(entry.getKey());
            }
            Collections.sort(unsortedGroups);
            responseb.addAllGroup(unsortedGroups);
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/archive/:instance/parameters/:name*/samples")
    public void getParameterSamples(RestRequest req) throws HttpException {
        if (isReplayAsked(req)) {
            aprh.getParameterSamples(req);
            return;
        }

        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);

        ParameterWithId pid = verifyParameterWithId(req, mdb, req.getRouteParam("name"));

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

        long start = req.getQueryParameterAsDate("start", defaultStart);
        long stop = req.getQueryParameterAsDate("stop", defaultStop);
        int sampleCount = req.getQueryParameterAsInt("count", 500);

        RestDownsampler sampler = new RestDownsampler(start, stop, sampleCount);
        ParameterArchive parchive = getParameterArchive(instance);
        ParameterCache pcache = getParameterCache(instance, req);

        ParameterRequest pr = new ParameterRequest(start, stop, true, true, false, false);
        SingleParameterRetriever spdr = new SingleParameterRetriever(parchive, pcache, pid, pr);
        try {
            spdr.retrieve(sampler);
        } catch (IOException e) {
            log.warn("Received exception during parameter retrieval ", e);
            throw new InternalServerErrorException(e.getMessage());
        }

        TimeSeries.Builder series = TimeSeries.newBuilder();
        for (Sample s : sampler.collect()) {
            series.addSample(ArchiveHelper.toGPBSample(s));
        }

        completeOK(req, series.build());
    }

    @Route(path = "/api/archive/:instance/parameters/:name*/ranges")
    public void getParameterRanges(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);

        ParameterWithId pid = verifyParameterWithId(req, mdb, req.getRouteParam("name"));

        long start = req.getQueryParameterAsDate("start", 0);
        long stop = req.getQueryParameterAsDate("stop", TimeEncoding.getWallclockTime());

        long minGap = req.getQueryParameterAsLong("minGap", 0);
        long maxGap = req.getQueryParameterAsLong("maxGap", Long.MAX_VALUE);

        ParameterArchive parchive = getParameterArchive(instance);
        ParameterCache pcache = getParameterCache(instance, req);

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
            ranges.addRange(ArchiveHelper.toGPBRange(r));
        }

        completeOK(req, ranges.build());
    }

    private ParameterArchive getParameterArchive(String instance) throws BadRequestException {
        List<ParameterArchive> l = yamcsServer.getServices(instance, ParameterArchive.class);
        if (l.isEmpty()) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }
        return l.get(0);
    }

    @Route(path = "/api/archive/:instance/parameters/:name*")
    public void listParameterHistory(RestRequest req) throws HttpException {
        if (isReplayAsked(req)) {
            aprh.listParameterHistory(req);
            return;
        }

        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        ParameterWithId requestedParamWithId = verifyParameterWithId(req, mdb,
                req.getRouteParam("name"));

        NamedObjectId requestedId = requestedParamWithId.getId();

        if (req.hasQueryParameter("pos")) {
            throw new BadRequestException("pos not supported");
        }
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean noRepeat = req.getQueryParameterAsBoolean("norepeat", false);
        long start = req.getQueryParameterAsDate("start", 0);
        long stop = req.getQueryParameterAsDate("stop", TimeEncoding.getWallclockTime());

        boolean ascending = !req.asksDescending(true);

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
                throw new NotFoundException(req);
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

        Processor realtimeProcessor = getRealtimeProc(instance, req);
        ParameterCache pcache = null;
        if (realtimeProcessor != null) {
            pcache = realtimeProcessor.getParameterCache();
        }
        if (req.asksFor(MediaType.CSV)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)))) {
                List<NamedObjectId> idList = Arrays.asList(requestedId);
                ParameterFormatter csvFormatter = new ParameterFormatter(bw, idList);
                limit++; // Allow one extra line for the CSV header
                RestParameterReplayListener replayListener = new RestParameterReplayListener(0, limit, req) {
                    @Override
                    public void onParameterData(ParameterValueWithId pvwid) {
                        try {
                            List<org.yamcs.protobuf.Pvalue.ParameterValue> pvlist = new ArrayList<>(1);
                            pvlist.add(pvwid.toGbpParameterValue());
                            csvFormatter.writeParameters(pvlist);
                        } catch (IOException e) {
                            log.error("Error while writing parameter line", e);
                        }
                    }
                };

                replayListener.setNoRepeat(noRepeat);
                // FIXME - make async
                retrieveParameterData(parchive, pcache, requestedParamWithId, mpvr, replayListener);

            } catch (IOException | DecodingException | RocksDBException e) {
                throw new InternalServerErrorException(e);
            }
            completeOK(req, MediaType.CSV, buf);
        } else {
            ListParameterValuesResponse.Builder resultb = ListParameterValuesResponse.newBuilder();
            final int fLimit = limit + 1; // one extra to detect continuation token
            try {
                RestParameterReplayListener replayListener = new RestParameterReplayListener(0, fLimit, req) {
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
                    public void update(ParameterValueWithId pvwid) {
                        super.update(pvwid);
                    }
                };

                replayListener.setNoRepeat(noRepeat);
                // FIXME - make async
                retrieveParameterData(parchive, pcache, requestedParamWithId, mpvr, replayListener);
            } catch (DecodingException | RocksDBException | IOException e) {
                throw new InternalServerErrorException(e);
            }
            completeOK(req, resultb.build());
        }
    }

    private void retrieveParameterData(ParameterArchive parchive, ParameterCache pcache, ParameterWithId pid,
            MultipleParameterValueRequest mpvr, RestParameterReplayListener replayListener)
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
            long stop, RestParameterReplayListener replayListener) {
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

    
    private void sendToListener(ParameterValue pv, ParameterWithId pid, RestParameterReplayListener replayListener) {
        ParameterValue pv1;
        if(pid.getPath()!=null) {
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
    
    
    private static ParameterCache getParameterCache(String instance, RestRequest req) throws NotFoundException {
        ParameterCache pcache = null;
        Processor realtimeProcessor = getRealtimeProc(instance, req);
        if (realtimeProcessor != null) {
            pcache = realtimeProcessor.getParameterCache();
        }
        return pcache;
    }

    private static Processor getRealtimeProc(String instance, RestRequest req) throws NotFoundException {
        String processorName;
        if (req.hasQueryParameter("norealtime")) {
            return null;
        } else {
            if (req.hasQueryParameter("processor")) {
                processorName = req.getQueryParameter("processor");
            } else {
                processorName = DEFAULT_PROCESSOR;
            }
        }
        return Processor.getInstance(instance, processorName);
    }

    private boolean isReplayAsked(RestRequest req) throws HttpException {
        if (!req.hasQueryParameter("source")) {
            return false;
        }

        String source = req.getQueryParameter("source");

        if (source.equalsIgnoreCase("ParameterArchive")) {
            return false;
        } else if (source.equalsIgnoreCase("replay")) {
            return true;
        } else {
            throw new BadRequestException(
                    "Bad value for parameter 'source'; valid values are: 'ParameterArchive' or 'replay'");
        }
    }
}
