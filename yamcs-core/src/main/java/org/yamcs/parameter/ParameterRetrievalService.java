package org.yamcs.parameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.ReplayOptions;
import org.yamcs.parameterarchive.ConsumerAbortException;
import org.yamcs.parameterarchive.MultiParameterRetrieval;
import org.yamcs.parameterarchive.MultipleParameterRequest;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterId;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.SingleParameterRetrieval;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.time.Instant;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;

import com.google.common.collect.Lists;

/**
 * Combines retrieval from different sources:
 * <ul>
 * <li>Parameter Archive</li>
 * <li>Replays</li>
 * <li>Parameter cache</li>
 * <li>Realtime Parameter Archive filler</li>
 * </ul>
 * 
 */
public class ParameterRetrievalService extends AbstractYamcsService {
    private static final String DEFAULT_PROCESSOR = "realtime";

    String procName = DEFAULT_PROCESSOR;
    ArrayParameterCache pcache;
    ParameterArchive parchive;
    ParameterCacheConfig cacheConfig;
    ExecutorService executor;
    static AtomicInteger count = new AtomicInteger();

    // if this is true, then we stick to the cacheConfig discovered during init
    // if this is false (meaning no explicit cache has been configured) then we set a cache if the realtime archive
    // filler is not enabled
    boolean pcacheConfigured;

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        this.procName = config.getString("processor", DEFAULT_PROCESSOR);

        int parallelRetrievals = config.getInt("parallelRetrievals", 4);
        this.executor = createExecutor(parallelRetrievals);
        if (config.containsKey("parameterCache")) {
            pcacheConfigured = true;
            YConfiguration pcacheConfig = config.getConfig("parameterCache");
            if (pcacheConfig.getBoolean("enabled", true)) {
                this.cacheConfig = new ParameterCacheConfig(pcacheConfig, log);
            }
        } else {
            pcacheConfigured = false;
        }
    }

    @Override
    protected void doStart() {
        var ysi = YamcsServer.getServer().getInstance(yamcsInstance);
        var l = ysi.getServices(ParameterArchive.class);
        if (!l.isEmpty()) {
            parchive = l.get(0);
        } else {
            log.info("The parameter archive service was not found");
        }
        if ((parchive == null || parchive.getRealtimeFiller() == null) && !pcacheConfigured) {
            cacheConfig = new ParameterCacheConfig(YConfiguration.emptyConfig(), log);
        }

        if (cacheConfig != null) {
            pcache = new ArrayParameterCache(yamcsInstance, cacheConfig);
            var proc = ysi.getProcessor(procName);
            if (proc == null) {
                log.info("No processor '" + procName + '"');
            } else {
                proc.getParameterRequestManager()
                        .subscribeAll((id, items) -> pcache.update(items));
            }
        }

        notifyStarted();
    }

    @Override
    protected void doStop() {
        executor.shutdown();
        notifyStopped();
    }

    /**
     * Retrieves a single scalar parameter or aggregate/array member.
     */
    public CompletableFuture<Void> retrieveScalar(ParameterWithId pid, ParameterRetrievalOptions opts,
            Consumer<ParameterValueArray> consumer) {
        log.debug("retrieveScalar pid: {}, opts: {} ", pid, opts);
        var cf = new CompletableFuture<Void>();
        executor.submit(() -> {
            try {
                if (parchive == null || opts.noparchive()) {
                    retrieveScalarReplayOrCache(pid, opts, consumer);
                } else if (parchive.getRealtimeFiller() != null) {
                    retrieveScalarParameterArchive(pid, opts, consumer);
                } else {
                    long coverageEnd = parchive.coverageEnd();

                    if (opts.ascending()) {
                        // ascending case -> retrieve max possible from the parameter archive
                        var tc = retrieveScalarParameterArchive(pid, opts, consumer);
                        // then from cache or via replay
                        if (tc.isValid()) {
                            if (opts.stop() > tc.time && opts.stop() > coverageEnd) {
                                var opts1 = opts.withUpdatedStart(tc.time + 1);
                                retrieveScalarReplayOrCache(pid, opts1, consumer);
                            }
                        } else {// no data retrieved from the parameter archive
                            retrieveScalarReplayOrCache(pid, opts, consumer);
                        }
                    } else {
                        // descending case
                        // if the request is beyond parameter archive coverage, retrieve first by cache or replay
                        if (opts.stop() > coverageEnd) {
                            if (opts.start() >= coverageEnd) {
                                // request does not overlap at all with the parameter archive coverage
                                retrieveScalarReplayOrCache(pid, opts, consumer);
                            } else {
                                // request overlaps with the parameter archive coverage
                                var req1 = opts.withUpdatedStart(coverageEnd);
                                retrieveScalarReplayOrCache(pid, req1, consumer);
                                var req2 = opts.withUpdatedStop(coverageEnd);
                                retrieveScalarParameterArchive(pid, req2, consumer);

                            }
                        } else {
                            // request can be satisfied only by parameter archive
                            retrieveScalarParameterArchive(pid, opts, consumer);
                        }
                    }
                }
                cf.complete(null);
            } catch (Exception e) {
                log.error("Error during retrieval", e);
                cf.completeExceptionally(e);
            }
        });
        return cf;
    }

    public CompletableFuture<Void> retrieveSingle(ParameterWithId pid, ParameterRetrievalOptions opts,
            Consumer<ParameterValueWithId> consumer) {
        log.debug("retrieveSingle requestedParamWithId: {}, opts: {}", pid, opts);

        var cf = new CompletableFuture<Void>();
        executor.submit(() -> {
            try {
                if (parchive == null || opts.noparchive()) {
                    retrieveSingleReplayOrCache(pid, opts, consumer);
                } else if (parchive.getRealtimeFiller() != null) {
                    retrieveSingleParameterArchive(pid, opts, consumer);
                } else {
                    long coverageEnd = parchive.coverageEnd();
                    if (opts.ascending()) {
                        // ascending case -> retrieve max possible from the parameter archive
                        var tc = retrieveSingleParameterArchive(pid, opts, consumer);
                        // then from cache or via replay
                        if (tc.isValid()) {
                            if (opts.stop() > tc.time && opts.stop() > coverageEnd) {
                                var req1 = opts.withUpdatedStart(tc.time + 1);
                                retrieveSingleReplayOrCache(pid, req1, consumer);
                            }
                        } else {
                            retrieveSingleReplayOrCache(pid, opts, consumer);
                        }
                    } else {
                        // descending case
                        // if the request is beyond parameter archive coverage, retrieve first by cache or replay
                        if (opts.stop() > coverageEnd) {
                            if (opts.start() >= coverageEnd) {
                                // request does not overlap at all with the parameter archive coverage
                                retrieveSingleReplayOrCache(pid, opts, consumer);
                            } else {
                                // request overlaps with the parameter archive coverage
                                var req1 = opts.withUpdatedStart(coverageEnd);
                                retrieveSingleReplayOrCache(pid, req1, consumer);
                                var req2 = opts.withUpdatedStop(coverageEnd);
                                retrieveSingleParameterArchive(pid, req2, consumer);

                            }
                        } else {
                            // request can be satisfied only by parameter archive
                            retrieveSingleParameterArchive(pid, opts, consumer);
                        }
                    }
                }

                cf.complete(null);
            } catch (ConsumerAbortException e) {
                cf.complete(null);
            } catch (Exception e) {
                log.error("Error during retrieval", e);
                cf.completeExceptionally(e);
            }
        });
        return cf;
    };

    public CompletableFuture<Void> retrieveMulti(List<ParameterWithId> pids, ParameterRetrievalOptions opts,
            Consumer<List<ParameterValueWithId>> consumer) {
        log.debug("retrieveMulti pids: {}, opts: {}", pids, opts);
        var cf = new CompletableFuture<Void>();
        executor.submit(() -> {
            try {
                if (parchive == null || opts.noparchive()) {
                    retrieveMultiReplayOrCache(pids, opts, consumer);
                } else if (parchive.getRealtimeFiller() != null) {
                    retrieveMultiParameterArchive(pids, opts, consumer);
                } else {
                    long coverageEnd = parchive.coverageEnd();
                    if (opts.ascending()) {
                        // ascending case -> retrieve max possible from the parameter archive
                        var tc = retrieveMultiParameterArchive(pids, opts, consumer);
                        // then from cache or via replay
                        if (tc.isValid()) {
                            if (opts.stop() > tc.time && opts.stop() > coverageEnd) {
                                var req1 = opts.withUpdatedStart(tc.time + 1);
                                retrieveMultiReplayOrCache(pids, req1, consumer);
                            }
                        } else {
                            retrieveMultiReplayOrCache(pids, opts, consumer);
                        }
                    } else {
                        // descending case
                        // if the request is beyond parameter archive coverage, retrieve first by cache or replay
                        if (opts.stop() > coverageEnd) {
                            if (opts.start() >= coverageEnd) {
                                // request does not overlap at all with the parameter archive coverage
                                retrieveMultiReplayOrCache(pids, opts, consumer);
                            } else {
                                // request overlaps with the parameter archive coverage
                                var req1 = opts.withUpdatedStart(coverageEnd);
                                retrieveMultiReplayOrCache(pids, req1, consumer);
                                var req2 = opts.withUpdatedStop(coverageEnd);
                                retrieveMultiParameterArchive(pids, req2, consumer);

                            }
                        } else {
                            // request can be satisfied only by parameter archive
                            retrieveMultiParameterArchive(pids, opts, consumer);
                        }
                    }
                }

                cf.complete(null);
            } catch (ConsumerAbortException e) {
                cf.complete(null);
            } catch (Exception e) {
                log.error("Error during retrieval", e);
                cf.completeExceptionally(e);
            }
        });
        return cf;
    }

    public ParameterCache getParameterCache() {
        return pcache;
    }

    private TimeAndCount retrieveScalarParameterArchive(ParameterWithId pid, ParameterRetrievalOptions request,
            Consumer<ParameterValueArray> consumer) throws IOException {
        log.debug("retrieveScalarParameterArchive pid: {}, request: {}", pid, request);
        SingleParameterRetrieval spar = new SingleParameterRetrieval(parchive, pid.getQualifiedName(), request);
        TimeAndCount tc = new TimeAndCount(TimeEncoding.INVALID_INSTANT, 0);
        try {
            spar.retrieve(pva -> {
                long[] timestamps = pva.getTimestamps();
                tc.time = timestamps[timestamps.length - 1];
                tc.count += timestamps.length;
                consumer.accept(pva);
            });
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        return tc;
    }

    TimeAndCount retrieveScalarReplayOrCache(ParameterWithId pid, ParameterRetrievalOptions opts,
            Consumer<ParameterValueArray> consumer) throws Exception {

        log.debug("retrieveScalarReplayOrCache pid: {}, opts: {} pcache present: {}", pid, opts, pcache != null);
        if (pcache != null && !opts.norealtime()) {
            long start = opts.start();
            long stop = opts.stop();
            // parameter cache returns value in reverse order in (start, stop]
            // if ascending is required we have to retrieve [start, stop)
            if (opts.ascending()) {
                start--;
                stop--;
            }
            // TODO this could be optimised because the pcache stores already arrays of values
            var pvList = opts.noreplay() ? pcache.getAllValues(pid.getParameter(), start, stop)
                    : pcache.getAllValuesIfCovered(pid.getParameter(), start, stop);
            log.debug("pcache returned {} results", pvList == null ? null : pvList.size());
            if (pvList != null) {
                if (pid.getPath() != null) {
                    pvList = extractMembers(pvList, pid.getPath());
                }
                if (opts.ascending()) {
                    pvList = Lists.reverse(pvList);
                }
                return splitAndSend(pvList, consumer);
            } // else it means the cache does not cover the requested interval,
              // send everything via replay if allowed
        }

        if (!opts.noreplay()) {
            return replay(Collections.singletonList(pid), opts, new Consumer<List<ParameterValueWithId>>() {
                @Override
                public void accept(List<ParameterValueWithId> pvList) {
                    for (var pv : pvList) {
                        consumer.accept(toScalarPva(pv.getParameterValue(), opts));
                    }
                }
            });
        } else {
            // noreplay is specified and no data has been found in the cache
            return new TimeAndCount(TimeEncoding.INVALID_INSTANT, 0);
        }
    }

    private TimeAndCount retrieveSingleParameterArchive(ParameterWithId pid, ParameterRetrievalOptions opts,
            Consumer<ParameterValueWithId> consumer) throws RocksDBException, IOException {

        log.debug("retrieveSingleParameterArchive pid: {}, opts: {}", pid, opts);

        MultipleParameterRequest mpvr;
        ParameterIdDb piddb = parchive.getParameterIdDb();
        String qn = pid.getQualifiedName();
        ParameterId[] pids = piddb.get(qn);
        if (pids != null) {
            TimeAndCount tc = new TimeAndCount(TimeEncoding.INVALID_INSTANT, 0);
            mpvr = new MultipleParameterRequest(opts.start(), opts.stop(), pids, opts.ascending());
            MultiParameterRetrieval mpdr = new MultiParameterRetrieval(parchive, mpvr);
            mpdr.retrieve(pvList -> {
                tc.count += pvList.size();
                tc.time = pvList.time();
                for (var pv : pvList.getValues()) {
                    consumer.accept(new ParameterValueWithId(pv, pid.id));
                }
            });
            return tc;
        } else {
            return new TimeAndCount(TimeEncoding.INVALID_INSTANT, 0);
        }
    }

    TimeAndCount retrieveSingleReplayOrCache(ParameterWithId pid, ParameterRetrievalOptions opts,
            Consumer<ParameterValueWithId> consumer) throws Exception {

        log.debug("retrieveSingleReplayOrCache pid: {}, opts: {} pcache present: {}", pid, opts, pcache != null);
        if (pcache != null && !opts.norealtime()) {
            long start = opts.start();
            long stop = opts.stop();
            // parameter cache returns value in reverse order in (start, stop]
            // if ascending is required we have to retrieve [start, stop)
            if (opts.ascending()) {
                start--;
                stop--;
            }

            var pvList = opts.noreplay() ? pcache.getAllValues(pid.getParameter(), start, stop)
                    : pcache.getAllValuesIfCovered(pid.getParameter(), start, stop);
            log.debug("pcache returned {} results", pvList == null ? null : pvList.size());
            if (pvList != null) {
                if (pid.getPath() != null) {
                    pvList = extractMembers(pvList, pid.getPath());
                }
                if (opts.ascending()) {
                    for (int i = pvList.size() - 1; i >= 0; i--) {
                        var pv = pvList.get(i);
                        consumer.accept(new ParameterValueWithId(pv, pid.id));
                    }
                    return new TimeAndCount(pvList.get(0).getGenerationTime(), pvList.size());
                } else {
                    for (var pv : pvList) {
                        consumer.accept(new ParameterValueWithId(pv, pid.id));
                    }
                    return new TimeAndCount(pvList.get(pvList.size() - 1).getGenerationTime(), pvList.size());
                }
            } // else it means the cache does not cover the requested interval,
              // send everything via replay if allowed
        }

        if (!opts.noreplay()) {
            return replay(Collections.singletonList(pid), opts, new Consumer<List<ParameterValueWithId>>() {
                @Override
                public void accept(List<ParameterValueWithId> pvList) {
                    for (var pv : pvList) {
                        consumer.accept(pv);
                    }
                }
            });
        } else {
            // noreplay is specified and no data has been found in the cache
            return new TimeAndCount(Instant.MIN_INSTANT, 0);
        }
    }

    private TimeAndCount retrieveMultiParameterArchive(List<ParameterWithId> pidList, ParameterRetrievalOptions opts,
            Consumer<List<ParameterValueWithId>> consumer) throws RocksDBException, IOException {

        log.debug("retrieveMultiParameterArchive pid: {}, opts: {}", pidList, opts);
        MultipleParameterRequest mpvr;
        ParameterIdDb piddb = parchive.getParameterIdDb();
        List<ParameterId> parameterIds = new ArrayList<>();
        // map between the
        Map<Integer, List<ParameterWithId>> pidMapping = new HashMap<>();

        for (ParameterWithId pid : pidList) {
            String qn = pid.getQualifiedName();
            ParameterId[] pids = piddb.get(qn);
            if (pids != null) {
                parameterIds.addAll(Arrays.asList(pids));
                for (var paraid : pids) {
                    pidMapping.computeIfAbsent(paraid.getPid(), k -> new ArrayList<>()).add(pid);
                }
            }
        }

        if (!parameterIds.isEmpty()) {
            TimeAndCount tc = new TimeAndCount(TimeEncoding.INVALID_INSTANT, 0);
            mpvr = new MultipleParameterRequest(opts.start(), opts.stop(),
                    parameterIds.toArray(new ParameterId[0]), opts.ascending());
            MultiParameterRetrieval mpdr = new MultiParameterRetrieval(parchive, mpvr);

            mpdr.retrieve(pvList -> {
                tc.count += pvList.size();
                tc.time = pvList.time();
                List<ParameterValueWithId> pvl = new ArrayList<>();
                IntArray parchiveIds = pvList.getPids();
                var x = pvList.getValues();
                for (int i = 0; i < parchiveIds.size(); i++) {
                    var paraIdList = pidMapping.get(parchiveIds.get(i));
                    for (var paraId : paraIdList) {
                        pvl.add(new ParameterValueWithId(x.get(i), paraId.id));
                    }
                }
                consumer.accept(pvl);

            });
            return tc;
        } else {
            return new TimeAndCount(TimeEncoding.INVALID_INSTANT, 0);
        }
    }

    TimeAndCount retrieveMultiReplayOrCache(List<ParameterWithId> pids, ParameterRetrievalOptions opts,
            Consumer<List<ParameterValueWithId>> consumer) throws Exception {
        log.debug("retrieveSingleReplayOrCache pid: {}, opts: {} pcache present: {}", pids, opts, pcache != null);
        if (pcache != null && !opts.norealtime()) {
            long start = opts.start();
            long stop = opts.stop();
            // parameter cache returns value in reverse order in (start, stop]
            // if ascending is required we have to retrieve [start, stop)
            if (opts.ascending()) {
                start--;
                stop--;
            }
            Map<Parameter, List<ParameterWithId>> pidMapping = new HashMap<>();
            for (var pid : pids) {
                Parameter parameter = pid.getParameter();
                pidMapping.computeIfAbsent(parameter, k -> new ArrayList<>()).add(pid);
            }
            var parameters = new ArrayList<>(pidMapping.keySet());

            var pvListList = opts.noreplay() ? pcache.getAllValues(parameters, start, stop)
                    : pcache.getAllValuesIfCovered(parameters, start, stop);
            log.debug("pcache returned {} results", pvListList.size());
            if (opts.ascending()) {
                pvListList = Lists.reverse(pvListList);
            }
            if (!pvListList.isEmpty()) {
                long t = 0;
                for (var pvList : pvListList) {
                    var pvidList = new ArrayList<ParameterValueWithId>();
                    for (var pv : pvList) {
                        for (var pid : pidMapping.get(pv.getParameter())) {
                            if (pid.getPath() != null) {
                                pv = AggregateUtil.extractMember(pv, pid.getPath());
                            }
                            pvidList.add(new ParameterValueWithId(pv, pid.getId()));
                        }
                        t = pv.getGenerationTime();
                    }
                    consumer.accept(pvidList);
                }
                return new TimeAndCount(t, pvListList.size());
            } // else it means the cache does not cover the requested interval,
              // send everything via replay if allowed
        }

        if (!opts.noreplay()) {
            return replay(pids, opts, consumer);
        } else {
            // noreplay is specified and no data has been found in the cache
            return new TimeAndCount(Instant.MIN_INSTANT, 0);
        }
    }

    // splits the list in arrays of parameters having the same type
    private TimeAndCount splitAndSend(List<ParameterValue> pvlist, Consumer<ParameterValueArray> consumer) {
        int n = 0;
        int m = pvlist.size();
        ParameterValue pv0 = pvlist.get(n);

        for (int j = 1; j < m; j++) {
            ParameterValue pv = pvlist.get(j);
            if (differentType(pv0, pv)) {
                sendToConsumer(pvlist, n, j, consumer);
                pv0 = pv;
                n = j;
            }
        }
        sendToConsumer(pvlist, n, m, consumer);
        return new TimeAndCount(pvlist.get(0).getGenerationTime(), pvlist.size());
    }

    private void sendToConsumer(List<ParameterValue> pvlist, int n, int m, Consumer<ParameterValueArray> consumer) {
        ParameterValue pv0 = pvlist.get(n);
        ValueArray rawValues = null;
        if (pv0.getRawValue() != null) {
            rawValues = new ValueArray(pv0.getRawValue().getType(), m - n);
            for (int i = n; i < m; i++) {
                rawValues.setValue(i - n, pvlist.get(i).getRawValue());
            }
        }

        ValueArray engValues = null;
        if (pv0.getEngValue() != null) {
            engValues = new ValueArray(pv0.getEngValue().getType(), m - n);
            for (int i = n; i < m; i++) {
                engValues.setValue(i - n, pvlist.get(i).getEngValue());
            }
        }
        long[] timestamps = new long[m - n];
        var statuses = new org.yamcs.yarch.protobuf.Db.ParameterStatus[m - n];
        for (int i = n; i < m; i++) {
            ParameterValue pv = pvlist.get(i);
            timestamps[i - n] = pv.getGenerationTime();
            statuses[i - n] = pv.getStatus().toProtoBuf(false);
        }
        ParameterValueArray pva = new ParameterValueArray(timestamps, engValues, rawValues, statuses);
        consumer.accept(pva);
    }

    private boolean differentType(ParameterValue pv0, ParameterValue pv1) {
        return differentType(pv0.getRawValue(), pv1.getRawValue())
                || differentType(pv0.getEngValue(), pv1.getEngValue());
    }

    private boolean differentType(Value v1, Value v2) {
        if (v1 == null) {
            return v2 != null;
        }
        if (v2 == null) {
            return true;
        }

        return v1.getType() != v2.getType();
    }

    private List<ParameterValue> extractMembers(List<ParameterValue> pvlist, PathElement[] path) {
        List<ParameterValue> l = new ArrayList<ParameterValue>(pvlist.size());
        for (ParameterValue pv : pvlist) {
            ParameterValue pv1 = AggregateUtil.extractMember(pv, path);
            if (pv1 != null) {
                l.add(pv1);
            }
        }
        return l;
    }

    private TimeAndCount replay(List<ParameterWithId> paramList, ParameterRetrievalOptions opts,
            Consumer<List<ParameterValueWithId>> consumer) throws Exception {
        ReplayOptions replayOpts = ReplayOptions.getAfapReplay(opts.start(), opts.stop(), !opts.ascending());
        if (opts.packetReplayRequest() != null) {
            replayOpts.setPacketRequest(opts.packetReplayRequest());
        }

        var prrb = ParameterReplayRequest.newBuilder();
        Map<Parameter, List<ParameterWithId>> params = new HashMap<>();

        for (var pid : paramList) {
            prrb.addNameFilter(pid.id);
            params.computeIfAbsent(pid.getParameter(), k -> new ArrayList<>()).add(pid);
        }

        replayOpts.setParameterRequest(prrb.build());
        Processor processor = ProcessorFactory.create(yamcsInstance, "api_replay" + count.incrementAndGet(),
                "ArchiveRetrieval", "internal", replayOpts);

        TimeAndCount tc = new TimeAndCount(Instant.MIN_INSTANT, 0);

        ParameterConsumer prmConsumer = new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> pvalues) {
                List<ParameterValueWithId> pvaluesWithIds = new ArrayList<>(params.size());
                for (ParameterValue pv : pvalues) {
                    var pids = params.get(pv.getParameter());
                    if (pids != null) {
                        if (opts.ascending()) {
                            tc.time = Math.max(pv.getGenerationTime(), tc.time);
                        } else {
                            tc.time = Math.min(pv.getGenerationTime(), tc.time);
                        }
                        for (var pid : pids) {
                            if (pid.getPath() != null) {
                                pv = AggregateUtil.extractMember(pv, pid.getPath());
                            }
                            pvaluesWithIds.add(new ParameterValueWithId(pv, pid.getId()));
                        }
                    }
                }
                try {
                    consumer.accept(pvaluesWithIds);
                } catch (ConsumerAbortException e) {
                    processor.quit();
                }
                ;
            }
        };
        processor.getParameterRequestManager().addRequest(params.keySet(), prmConsumer);
        processor.startAsync();
        processor.awaitTerminated();

        return tc;
    }

    private ParameterValueArray toScalarPva(ParameterValue pv, ParameterRetrievalOptions opts) {
        long[] timestamps = new long[] { pv.getGenerationTime() };
        ValueArray engValues = null;

        if (opts.retrieveEngValues() && pv.getEngValue() != null) {
            engValues = new ValueArray(pv.getEngValue().getType(), 1);
            engValues.setValue(0, pv.getEngValue());
        }

        ValueArray rawValues = null;
        if (opts.retrieveRawValues() && pv.getRawValue() != null) {
            rawValues = new ValueArray(pv.getRawValue().getType(), 1);
            rawValues.setValue(0, pv.getRawValue());
        }

        org.yamcs.yarch.protobuf.Db.ParameterStatus[] paramStatus = null;

        if (opts.retrieveParameterStatus()) {
            paramStatus = new org.yamcs.yarch.protobuf.Db.ParameterStatus[] {
                    pv.getStatus().toProtoBuf(false) };
        }

        return new ParameterValueArray(timestamps, engValues, rawValues, paramStatus);
    }

    private ExecutorService createExecutor(int numThreads) {
        return Executors.newFixedThreadPool(numThreads, new ThreadFactory() {
            private int count = 1;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("ParameterRetrievalService-" + count++);
                return thread;
            }
        });
    }

    static final class TimeAndCount {
        long time;
        int count;

        public TimeAndCount(long time, int count) {
            this.time = time;
            this.count = count;
        }

        public boolean isValid() {
            return this.time != TimeEncoding.INVALID_INSTANT;
        }

        @Override
        public String toString() {
            return "TimeAndCount [time=" + TimeEncoding.toString(time) + ", count=" + count + "]";
        }
    }
}
