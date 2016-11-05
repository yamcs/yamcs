package org.yamcs.web.rest.archive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.parameter.ParameterCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameterarchive.ConsumerAbortException;
import org.yamcs.parameterarchive.MultiParameterDataRetrieval;
import org.yamcs.parameterarchive.MultipleParameterValueRequest;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterGroupIdDb;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterIdDb.ParameterId;
import org.yamcs.parameterarchive.ParameterIdValueList;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.SingleParameterDataRetrieval;
import org.yamcs.parameterarchive.SingleParameterValueRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.IntArray;
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
import org.yamcs.web.rest.archive.RestDownsampler.Sample;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

public class ArchiveParameter2RestHandler extends RestHandler {
    private static final String DEFAULT_PROCESSOR = "realtime";
    private static final Logger log = LoggerFactory.getLogger(ArchiveParameter2RestHandler.class);

    /**
     * A series is a list of samples that are determined in one-pass while processing a stream result.
     * Final API unstable.
     * <p>
     * If no query parameters are defined, the series covers *all* data.
     */
    @Route(path = "/api/archive/:instance/parameters2/:name*/samples")
    public void getParameterSamples(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));
        YProcessor realtimeProcessor = getRealtimeProc(instance, req);

        /*
        TODO check commented out, in order to support sampling system parameters
        which don't have a type
        
        ParameterType ptype = p.getParameterType();
        if (ptype == null) {
            throw new BadRequestException("Requested parameter has no type");
        } else if (!(ptype instanceof FloatParameterType) && !(ptype instanceof IntegerParameterType)) {
            throw new BadRequestException("Only integer or float parameters can be sampled. Got " + ptype.getTypeAsString());
        }*/

        long start = req.getQueryParameterAsDate("start", 0);
        long stop = req.getQueryParameterAsDate("stop", TimeEncoding.getWallclockTime());

        RestDownsampler sampler = new RestDownsampler(stop);
        ParameterArchive parchive = getParameterArchive(instance);
        ParameterIdDb piddb = parchive.getParameterIdDb();

        ParameterCache pcache = null;
        if(realtimeProcessor!=null) {
            pcache = realtimeProcessor.getParameterCache();
        }

        ParameterId[] pids = piddb.get(p.getQualifiedName());
        if(pids == null) {
            log.warn("No parameter id found in the parameter archive for {}", p.getQualifiedName());
            if(pcache!=null) {
                sampleDataFromCache(pcache, p, start, stop, sampler);
            }
        } else {
            ParameterGroupIdDb pgidDb = parchive.getParameterGroupIdDb();
            for(ParameterId pid: pids) {
                int parameterId = pid.pid;
                Value.Type engType = pids[0].engType;

                int[] pgids = pgidDb.getAllGroups(parameterId);
                if(pgids.length ==0 ){
                    log.error("Found no parameter group for parameter Id {}", parameterId);
                    continue;
                }
                log.info("Executing a single parameter value request for time interval [{} - {}] parameterId: {} and parameter groups: {}", TimeEncoding.toString(start), TimeEncoding.toString(stop), parameterId, Arrays.toString(pgids));
                SingleParameterValueRequest spvr = new SingleParameterValueRequest(start, stop, parameterId, pgids, true);
                sampleDataForParameterId(parchive, engType, spvr, sampler);
                if(pcache!=null) {
                    sampleDataFromCache(pcache, p, start, stop, sampler);
                }
            }
        }

        TimeSeries.Builder series = TimeSeries.newBuilder();
        for (Sample s : sampler.collect()) {
            series.addSample(ArchiveHelper.toGPBSample(s));
        }

        completeOK(req, series.build(), SchemaPvalue.TimeSeries.WRITE);
    }

    private void sampleDataFromCache(ParameterCache pcache, Parameter p, long start, long stop, RestDownsampler sampler) {
        //grab some data from the realtime processor cache
        List<org.yamcs.parameter.ParameterValue> pvlist = pcache.getAllValues(p);
        if(pvlist!=null) {
            int n = pvlist.size();
            for(int i = n-1; i>=0; i--) {
                org.yamcs.parameter.ParameterValue pv = pvlist.get(i);
                if(pv.getGenerationTime() < start) continue;
                if(pv.getGenerationTime() > stop) break;
                if(pv.getGenerationTime() > sampler.lastSampleTime()) {
                    sampler.process(pv);
                }
            }
        }
    }

    private void sampleDataForParameterId(ParameterArchive parchive, Value.Type engType, SingleParameterValueRequest spvr, RestDownsampler sampler) throws HttpException {
        spvr.setRetrieveEngineeringValues(true);
        spvr.setRetrieveParameterStatus(false);
        spvr.setRetrieveRawValues(false);
        SingleParameterDataRetrieval spdr = new SingleParameterDataRetrieval(parchive, spvr);
        try {
            spdr.retrieve(new Consumer<ParameterValueArray>() {
                @Override
                public void accept(ParameterValueArray t) {

                    Object o = t.getEngValues();
                    long[] timestamps = t.getTimestamps();
                    int n = timestamps.length;
                    if(o instanceof float[]) {
                        float[] values = (float[])o;
                        for(int i=0;i<n;i++) {
                            sampler.process(timestamps[i], values[i]);
                        }
                    } else if(o instanceof double[]) {
                        double[] values = (double[])o;
                        for(int i=0;i<n;i++) {
                            sampler.process(timestamps[i], values[i]);
                        }
                    } else if(o instanceof long[]) {
                        long[] values = (long[])o;
                        for(int i=0;i<n;i++) {
                            if(engType==Type.UINT64) {
                                sampler.process(timestamps[i], unsignedLongToDouble(values[i]));
                            } else {
                                sampler.process(timestamps[i], values[i]);
                            }
                        }
                    } else if(o instanceof int[]) {
                        int[] values = (int[])o;
                        for(int i=0;i<n;i++) {
                            if(engType==Type.UINT32) {
                                sampler.process(timestamps[i], values[i]&0xFFFFFFFFL);
                            } else {
                                sampler.process(timestamps[i], values[i]);
                            }
                        }
                    } else {
                        log.warn("Unexpected value type " + o.getClass());
                    }

                }
            });
        } catch (RocksDBException | DecodingException e) {
            log.warn("Received exception during parmaeter retrieval ", e);
            throw new InternalServerErrorException(e.getMessage());
        }

    }
    private static ParameterArchive getParameterArchive(String instance) throws BadRequestException {
        ParameterArchive parameterArchive = YamcsServer.getService(instance, ParameterArchive.class);
        if (parameterArchive == null) {
            throw new BadRequestException("ParameterArchive not configured for this instance");
        }
        return parameterArchive;
    }

    /**copied from guava*/
    double unsignedLongToDouble(long x) {
        double d = (double) (x & 0x7fffffffffffffffL);
        if (x < 0) {
            d += 0x1.0p63;
        }
        return d;
    }
    @Route(path = "/api/archive/:instance/parameters2/:name*")
    public void listParameterHistory(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        NameDescriptionWithId<Parameter> requestedParamWithId = verifyParameterWithId(req, mdb, req.getRouteParam("name"));
        
        Parameter p = requestedParamWithId.getItem();
        NamedObjectId requestedId = requestedParamWithId.getRequestedId();
        
        if(req.hasQueryParameter("pos")) throw new BadRequestException("pos not supported");
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean noRepeat = req.getQueryParameterAsBoolean("norepeat", false);
        long start = req.getQueryParameterAsDate("start", 0);
        long stop = req.getQueryParameterAsDate("stop", TimeEncoding.getWallclockTime());

        boolean ascending = !req.asksDescending(true);

        ParameterArchive parchive = getParameterArchive(instance);
        ParameterIdDb piddb = parchive.getParameterIdDb();
        IntArray pidArray = new IntArray();
        IntArray pgidArray = new IntArray();

        ParameterId[] pids = piddb.get(p.getQualifiedName());
        if(pids != null) {
            
            ParameterGroupIdDb pgidDb = parchive.getParameterGroupIdDb();

            for(ParameterId pid:pids) {
                int[] pgids = pgidDb.getAllGroups(pid.pid);
                for(int pgid: pgids) {
                    pidArray.add(pid.pid);
                    pgidArray.add(pgid);
                }
            }

            if(pidArray.isEmpty()) {
                log.error("No parameter group id found in the parameter archive for {}", p.getQualifiedName());
                throw new NotFoundException(req);
            }
        } else {
            log.warn("No parameter id found in the parameter archive for {}", p.getQualifiedName());
        }
        String[] pnames = new String[pidArray.size()];
        Arrays.fill(pnames, p.getQualifiedName());
        MultipleParameterValueRequest mpvr = new MultipleParameterValueRequest(start, stop, pnames, pidArray.toArray(), pgidArray.toArray(), ascending);
        mpvr.setRetrieveRawValues(true);
        // do not use set limit because the data can be filtered down (e.g. noRepeat) and the limit applies the final filtered data not to the input
        // one day the parameter archive will be smarter and do the filtering inside
        //mpvr.setLimit(limit);


        YProcessor realtimeProcessor = getRealtimeProc(instance, req);
        ParameterCache pcache = null;
        if(realtimeProcessor!=null) {
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
                //FIXME - make async
                retrieveParameterData(parchive, pcache, p, requestedId, mpvr, replayListener);

            } catch (IOException|DecodingException|RocksDBException e) {
                throw new InternalServerErrorException(e);
            }
            completeOK(req, MediaType.CSV, buf);
        } else {
            ParameterData.Builder resultb = ParameterData.newBuilder();
            try {
                RestParameterReplayListener replayListener = new RestParameterReplayListener(0, limit, req) {
                    @Override
                    public void onParameterData(ParameterValueWithId  pvwid) {
                        resultb.addParameter(pvwid.toGbpParameterValue());
                    }

                    @Override
                    public void update(ParameterValueWithId pvwid) {
                        super.update(pvwid);
                    }
                };

                replayListener.setNoRepeat(noRepeat);
              //FIXME - make async
                retrieveParameterData(parchive, pcache, p, requestedId, mpvr, replayListener);
            } catch (DecodingException|RocksDBException e) {
                throw new InternalServerErrorException(e);
            }
            completeOK(req, resultb.build(), SchemaPvalue.ParameterData.WRITE);
        }
    }


    private void retrieveParameterData(ParameterArchive parchive,  ParameterCache pcache, Parameter p, NamedObjectId id,
            MultipleParameterValueRequest mpvr, RestParameterReplayListener replayListener) throws RocksDBException, DecodingException {


        MutableLong lastParameterTime = new MutableLong(TimeEncoding.INVALID_INSTANT);
        Consumer<ParameterIdValueList> consumer = new Consumer<ParameterIdValueList>() {
            boolean first = true;
            @Override
            public void accept(ParameterIdValueList pidvList) {
                lastParameterTime.l = pidvList.getValues().get(0).getGenerationTime();
                if(first && !mpvr.isAscending() && (pcache!=null)) { //retrieve data from cache first
                    first = false;
                    sendFromCache(p, id, pcache, false, lastParameterTime.l, mpvr.getStop(), replayListener);
                }
                ParameterValue pv = pidvList.getValues().get(0);
                replayListener.update(new ParameterValueWithId(pv, id));
                if(replayListener.isReplayAbortRequested()) throw new ConsumerAbortException();
            }
        };
        MultiParameterDataRetrieval mpdr = new MultiParameterDataRetrieval(parchive, mpvr);
        mpdr.retrieve(consumer);

        //now add some data from cache
        if (pcache!=null) {
            if(mpvr.isAscending())  {
                sendFromCache(p, id, pcache, true, lastParameterTime.l, mpvr.getStop(), replayListener);      
            } else if (lastParameterTime.l==TimeEncoding.INVALID_INSTANT) {  //no data retrieved from archive, but maybe there is still something in the cache to send
                sendFromCache(p, id, pcache, false, mpvr.getStart(), mpvr.getStop(), replayListener);
            }
        }
    }

    //send data from cache with timestamps in (start, stop) if ascending or (start, stop] if descending interval 
    private void sendFromCache(Parameter p, NamedObjectId id, ParameterCache pcache, boolean ascending, long start, long stop, RestParameterReplayListener replayListener) {
        List<ParameterValue> pvlist = pcache.getAllValues(p);
        if(pvlist==null) return;

        if(ascending) {
            int n = pvlist.size();
            for(int i = n-1; i>=0 ; i-- ) {
                org.yamcs.parameter.ParameterValue pv = pvlist.get(i);
                if(pv.getGenerationTime() >= stop) break;
                if(pv.getGenerationTime()> start) {
                    replayListener.update(new ParameterValueWithId(pv, id));
                    if(replayListener.isReplayAbortRequested()) break;
                }
            }
        } else {
            for(ParameterValue pv:pvlist) {
                if(pv.getGenerationTime()>stop) continue;
                if(pv.getGenerationTime() <= start) break;
                replayListener.update(new ParameterValueWithId(pv, id));
                if(replayListener.isReplayAbortRequested()) break;
            }
        }
    }
    private YProcessor getRealtimeProc(String instance, RestRequest req) throws NotFoundException {
        String processorName;
        if(req.hasQueryParameter("norealtime")) {
            return null;
        } else {
            if(req.hasQueryParameter("processor")) {
                processorName = req.getQueryParameter("processor");
            } else {
                processorName = DEFAULT_PROCESSOR;
            }
        }
        return YProcessor.getInstance(instance, processorName);
    }

    private class MutableLong {
        long l;
        public MutableLong(long l) {
            this.l = l;
        }
    }
}