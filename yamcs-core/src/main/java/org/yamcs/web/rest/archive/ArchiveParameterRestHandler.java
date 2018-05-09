package org.yamcs.web.rest.archive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameterarchive.ConsumerAbortException;
import org.yamcs.parameterarchive.MultiParameterDataRetrieval;
import org.yamcs.parameterarchive.MultipleParameterValueRequest;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterArchiveV2;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterId;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterIdValueList;
import org.yamcs.parameterarchive.ParameterRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
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
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

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
    private OldArchiveParameterRestHandler oldaprh = new OldArchiveParameterRestHandler();

    /**
     * A series is a list of samples that are determined in one-pass while processing a stream result. Final API
     * unstable.
     * <p>
     * If no query parameters are defined, the series covers *all* data.
     * 
     * @param req
     * @throws HttpException
     */
    @Route(path = "/api/archive/:instance/parameters/:name*/samples")
    public void getParameterSamples(RestRequest req) throws HttpException {
        if (isReplayAsked(req)) {
            aprh.getParameterSamples(req);
            return;
        }

        String instance = verifyInstance(req, req.getRouteParam("instance"));
        if (isOldParameterArchive(instance)) {
            oldaprh.getParameterSamples(req);
            return;
        }

        XtceDb mdb = XtceDbFactory.getInstance(instance);

        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));

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
        ParameterArchiveV2 parchive = getParameterArchive(instance);
        ParameterCache pcache = getParameterCache(instance, req);

        ParameterRequest pr = new ParameterRequest(start, stop, true, true, false, false);
        SingleParameterRetriever spdr = new SingleParameterRetriever(parchive, pcache, p, pr);
        try {
            spdr.retrieve(sampler);
        } catch (IOException e) {
            log.warn("Received exception during parmaeter retrieval ", e);
            throw new InternalServerErrorException(e.getMessage());
        }

        TimeSeries.Builder series = TimeSeries.newBuilder();
        for (Sample s : sampler.collect()) {
            series.addSample(ArchiveHelper.toGPBSample(s));
        }

        completeOK(req, series.build());
    }

    /**
     * A series is a list of samples that are determined in one-pass while processing a stream result. Final API
     * unstable.
     * <p>
     * If no query parameters are defined, the series covers *all* data.
     * 
     * @param req
     * @throws HttpException
     */
    @Route(path = "/api/archive/:instance/parameters/:name*/ranges")
    public void getParameterRanges(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        if (isOldParameterArchive(instance)) {
            throw new BadRequestException("Ranges not supported for the old parameter archive");
        }

        XtceDb mdb = XtceDbFactory.getInstance(instance);

        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));

        long start = req.getQueryParameterAsDate("start", 0);
        long stop = req.getQueryParameterAsDate("stop", TimeEncoding.getWallclockTime());

        long minGap = req.getQueryParameterAsLong("minGap", 0);
        long maxGap = req.getQueryParameterAsLong("maxGap", Long.MAX_VALUE);

        ParameterArchiveV2 parchive = getParameterArchive(instance);
        ParameterCache pcache = getParameterCache(instance, req);

        ParameterRanger ranger = new ParameterRanger(minGap, maxGap);

        ParameterRequest pr = new ParameterRequest(start, stop, true, true, false, true);
        SingleParameterRetriever spdr = new SingleParameterRetriever(parchive, pcache, p, pr);
        try {
            spdr.retrieve(ranger);
        } catch (IOException e) {
            log.warn("Received exception during parmaeter retrieval ", e);
            throw new InternalServerErrorException(e.getMessage());
        }

        Ranges.Builder ranges = Ranges.newBuilder();
        for (Range r : ranger.getRanges()) {
            ranges.addRange(ArchiveHelper.toGPBRange(r));
        }

        completeOK(req, ranges.build());
    }

    private boolean isOldParameterArchive(String instance) throws BadRequestException {
        ParameterArchive parameterArchive = YamcsServer.getService(instance, ParameterArchive.class);

        if (parameterArchive == null) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }
        return parameterArchive.getParchive() instanceof org.yamcs.oldparchive.ParameterArchive;
    }

    private static ParameterArchiveV2 getParameterArchive(String instance) throws BadRequestException {
        ParameterArchive parameterArchive = YamcsServer.getService(instance, ParameterArchive.class);
        if (parameterArchive == null) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }
        return (ParameterArchiveV2) parameterArchive.getParchive();
    }

    @Route(path = "/api/archive/:instance/parameters/:name*")
    public void listParameterHistory(RestRequest req) throws HttpException {
        if (isReplayAsked(req)) {
            aprh.listParameterHistory(req);
            return;
        }

        String instance = verifyInstance(req, req.getRouteParam("instance"));
        if (isOldParameterArchive(instance)) {
            oldaprh.listParameterHistory(req);
            return;
        }

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        NameDescriptionWithId<Parameter> requestedParamWithId = verifyParameterWithId(req, mdb,
                req.getRouteParam("name"));

        Parameter p = requestedParamWithId.getItem();
        NamedObjectId requestedId = requestedParamWithId.getRequestedId();

        if (req.hasQueryParameter("pos")) {
            throw new BadRequestException("pos not supported");
        }
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean noRepeat = req.getQueryParameterAsBoolean("norepeat", false);
        long start = req.getQueryParameterAsDate("start", 0);
        long stop = req.getQueryParameterAsDate("stop", TimeEncoding.getWallclockTime());

        boolean ascending = !req.asksDescending(true);

        ParameterArchiveV2 parchive = getParameterArchive(instance);
        ParameterIdDb piddb = parchive.getParameterIdDb();
        IntArray pidArray = new IntArray();
        IntArray pgidArray = new IntArray();

        ParameterId[] pids = piddb.get(p.getQualifiedName());

        BitSet retriveRawValues = new BitSet();
        if (pids != null) {
            ParameterGroupIdDb pgidDb = parchive.getParameterGroupIdDb();
            for (ParameterId pid : pids) {
                int[] pgids = pgidDb.getAllGroups(pid.pid);
                for (int pgid : pgids) {
                    if (pid.getRawType() != null) {
                        retriveRawValues.set(pidArray.size());
                    }
                    pidArray.add(pid.pid);
                    pgidArray.add(pgid);
                }
            }

            if (pidArray.isEmpty()) {
                log.error("No parameter group id found in the parameter archive for {}", p.getQualifiedName());
                throw new NotFoundException(req);
            }
        } else {
            log.warn("No parameter id found in the parameter archive for {}", p.getQualifiedName());
        }
        String[] pnames = new String[pidArray.size()];
        Arrays.fill(pnames, p.getQualifiedName());
        MultipleParameterValueRequest mpvr = new MultipleParameterValueRequest(start, stop, pnames, pidArray.toArray(),
                pgidArray.toArray(), retriveRawValues, ascending);
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
                retrieveParameterData(parchive, pcache, p, requestedId, mpvr, replayListener);

            } catch (IOException | DecodingException | RocksDBException e) {
                throw new InternalServerErrorException(e);
            }
            completeOK(req, MediaType.CSV, buf);
        } else {
            ParameterData.Builder resultb = ParameterData.newBuilder();
            try {
                RestParameterReplayListener replayListener = new RestParameterReplayListener(0, limit, req) {
                    @Override
                    public void onParameterData(ParameterValueWithId pvwid) {
                        resultb.addParameter(pvwid.toGbpParameterValue());
                    }

                    @Override
                    public void update(ParameterValueWithId pvwid) {
                        super.update(pvwid);
                    }
                };

                replayListener.setNoRepeat(noRepeat);
                // FIXME - make async
                retrieveParameterData(parchive, pcache, p, requestedId, mpvr, replayListener);
            } catch (DecodingException | RocksDBException | IOException e) {
                throw new InternalServerErrorException(e);
            }
            completeOK(req, resultb.build());
        }
    }

    private void retrieveParameterData(ParameterArchiveV2 parchive, ParameterCache pcache, Parameter p,
            NamedObjectId id,
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
                    sendFromCache(p, id, pcache, false, lastParameterTime.getLong(), mpvr.getStop(), replayListener);
                }
                ParameterValue pv = pidvList.getValues().get(0);
                replayListener.update(new ParameterValueWithId(pv, id));
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
                sendFromCache(p, id, pcache, true, start, mpvr.getStop(), replayListener);
            } else if (lastParameterTime.getLong() == TimeEncoding.INVALID_INSTANT) { // no data retrieved from archive,
                                                                                      // but
                // maybe there is still something in the
                // cache to send
                sendFromCache(p, id, pcache, false, mpvr.getStart(), mpvr.getStop(), replayListener);
            }
        }
    }

    // send data from cache with timestamps in (start, stop) if ascending or (start, stop] if descending interval
    private void sendFromCache(Parameter p, NamedObjectId id, ParameterCache pcache, boolean ascending, long start,
            long stop, RestParameterReplayListener replayListener) {
        List<ParameterValue> pvlist = pcache.getAllValues(p);
        if (pvlist == null) {
            return;
        }

        if (ascending) {
            int n = pvlist.size();
            for (int i = n - 1; i >= 0; i--) {
                org.yamcs.parameter.ParameterValue pv = pvlist.get(i);
                if (pv.getGenerationTime() >= stop) {
                    break;
                }
                if (pv.getGenerationTime() > start) {
                    replayListener.update(new ParameterValueWithId(pv, id));
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
                replayListener.update(new ParameterValueWithId(pv, id));
                if (replayListener.isReplayAbortRequested()) {
                    break;
                }
            }
        }
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
