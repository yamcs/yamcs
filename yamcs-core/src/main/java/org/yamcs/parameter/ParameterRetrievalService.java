package org.yamcs.parameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
import org.yamcs.parameterarchive.ParameterIdValueList;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.SingleParameterRetrieval;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.time.Instant;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.MutableLong;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

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

    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        this.procName = config.getString("processor", DEFAULT_PROCESSOR);

        int parallelRetrievals = config.getInt("parallelRetrievals", 2);
        this.executor = createExecutor(parallelRetrievals);
        YConfiguration pcacheConfig = config.getConfigOrEmpty("parameterCache");
        if (pcacheConfig.getBoolean("enabled", true)) {
            this.cacheConfig = new ParameterCacheConfig(pcacheConfig, log);
        }
    }

    @Override
    protected void doStart() {
        var ysi = YamcsServer.getServer().getInstance(yamcsInstance);
        if (cacheConfig != null) {
            pcache = new ArrayParameterCache(yamcsInstance, cacheConfig);
            var proc = ysi.getProcessor(procName);
            proc.getParameterRequestManager()
                    .subscribeAll((id, items) -> pcache.update(items));
        }

        var l = ysi.getServices(ParameterArchive.class);
        if (!l.isEmpty()) {
            // parchive = l.get(0);
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
        System.out.println("retrieve scalar opts: " + opts);
        log.debug("retrieveScalar pid: {}, opts: {}", pid, opts);

        var cf = new CompletableFuture<Void>();
        executor.submit(() -> {
            try {
                if (parchive == null) {
                    retrieveScalarReplayOrCache(pid, opts, consumer);
                } else {
                    long coverageEnd = parchive.coverageEnd();
                    if (opts.ascending()) {
                        // ascending case -> retrieve max possible from the parameter archive
                        var tc = retrieveScalarParameterArchive(pid, opts, consumer);
                        if (opts.stop() > tc.time && opts.stop() > coverageEnd) {
                            var req1 = opts.withUpdatedStart(tc.time);
                            // then from cache or via replay
                            retrieveScalarReplayOrCache(pid, req1, consumer);
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
                cf.completeExceptionally(e);
            }
        });
        return cf;
    }

    public CompletableFuture<Void> retrieveSingle(ParameterWithId pid, ParameterRetrievalOptions opts,
            Consumer<ParameterValueWithId> consumer) {
        log.debug("retrieveSingle requestedParamWithId: {}, opts: {}", pid, opts);
        System.out.println("retrieve single pid:" + pid + " opts: " + opts);
        var cf = new CompletableFuture<Void>();
        executor.submit(() -> {
            try {
                if (parchive == null) {
                    retrieveSingleReplayOrCache(pid, opts, consumer);
                } else {
                    MultipleParameterRequest mpvr;
                    ParameterIdDb piddb = parchive.getParameterIdDb();
                    String qn = pid.getQualifiedName();
                    ParameterId[] pids = piddb.get(qn);
                    if (pids != null) {
                        mpvr = new MultipleParameterRequest(opts.start(), opts.stop(), pids, opts.ascending());
                        retrieveParameterData(parchive, pcache, pid, mpvr, consumer);
                    } else {
                        log.debug("No parameter id found in the parameter archive for {}", qn);
                        replay(Arrays.asList(pid), opts, pvList -> {
                            for (var pv : pvList) {
                                consumer.accept(pv);
                            }
                        });
                    }
                }
                cf.complete(null);
            } catch (ConsumerAbortException e) {
                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            }
        });
        return cf;
    };

    public void retrieveMulti(ParameterWithId requestedParamWithId, ParameterRetrievalOptions opts,
            Consumer<List<ParameterValueWithId>> consumer) {

    }

    TimeAndCount retrieveScalarReplayOrCache(ParameterWithId pid, ParameterRetrievalOptions opts,
            Consumer<ParameterValueArray> consumer) throws Exception {

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
            if (pvList != null) {

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

    TimeAndCount retrieveSingleReplayOrCache(ParameterWithId pid, ParameterRetrievalOptions opts,
            Consumer<ParameterValueWithId> consumer) throws Exception {
        System.out.println("retrieve single replay or cache pcache: " + pcache + " norplay: " + opts.noreplay());

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
            if (pvList != null) {

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

    private TimeAndCount retrieveScalarParameterArchive(ParameterWithId pid, ParameterRetrievalOptions request,
            Consumer<ParameterValueArray> consumer) throws IOException {
        SingleParameterRetrieval spar = new SingleParameterRetrieval(parchive, pid.getQualifiedName(), request);
        TimeAndCount tc = new TimeAndCount(Long.MAX_VALUE, 0);
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

    private TimeAndCount replay(List<ParameterWithId> paramList, ParameterRetrievalOptions opts,
            Consumer<List<ParameterValueWithId>> consumer) throws Exception {
        ReplayOptions replayOpts = ReplayOptions.getAfapReplay(opts.start(), opts.stop(), !opts.ascending());

        TimeAndCount tc = new TimeAndCount(Instant.MIN_INSTANT, 0);

        Map<Parameter, List<NamedObjectId>> params = paramList.stream()
                .collect(Collectors.groupingBy(
                        ParameterWithId::getParameter,
                        Collectors.mapping(ParameterWithId::getId, Collectors.toList())));
        Processor processor = ProcessorFactory.create(yamcsInstance, "api_replay" + count.incrementAndGet(),
                "ArchiveRetrieval", "internal", replayOpts);

        ParameterConsumer prmConsumer = new ParameterConsumer() {
            @Override
            public void updateItems(int subscriptionId, List<ParameterValue> pvalues) {
                List<ParameterValueWithId> pvaluesWithIds = new ArrayList<>(params.size());

                for (ParameterValue pv : pvalues) {
                    var ids = params.get(pv.getParameter());
                    if (ids != null) {
                        if (opts.ascending()) {
                            tc.time = Math.max(pv.getGenerationTime(), tc.time);
                        } else {
                            tc.time = Math.min(pv.getGenerationTime(), tc.time);
                        }
                        for (var id : ids) {
                            pvaluesWithIds.add(new ParameterValueWithId(pv, id));
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
        processor.awaitRunning();
        processor.awaitTerminated();

        return tc;
    }

    private void retrieveParameterData(ParameterArchive parchive, ParameterCache pcache, ParameterWithId pid,
            MultipleParameterRequest mpvr, Consumer<ParameterValueWithId> consumer)
            throws RocksDBException, DecodingException, IOException {

        MutableLong lastParameterTime = new MutableLong(TimeEncoding.INVALID_INSTANT);
        Consumer<ParameterIdValueList> consumer1 = new Consumer<>() {
            boolean first = true;

            @Override
            public void accept(ParameterIdValueList pidvList) {
                lastParameterTime.setLong(pidvList.getValues().get(0).getGenerationTime());
                if (first && !mpvr.isAscending() && (pcache != null)) { // retrieve data from cache first
                    first = false;
                    sendFromCache(pid, pcache, false, lastParameterTime.getLong(), mpvr.getStop(), consumer);
                }
                for (ParameterValue pv : pidvList.getValues()) {
                    consumer.accept(new ParameterValueWithId(pv, pid.getId()));
                }
            }
        };
        MultiParameterRetrieval mpdr = new MultiParameterRetrieval(parchive, mpvr);
        mpdr.retrieve(consumer1);

        // now add some data from cache
        if (pcache != null) {
            if (mpvr.isAscending()) {
                long start = (lastParameterTime.getLong() == TimeEncoding.INVALID_INSTANT) ? mpvr.getStart() - 1
                        : lastParameterTime.getLong();
                sendFromCache(pid, pcache, true, start, mpvr.getStop(), consumer);
            } else if (lastParameterTime.getLong() == TimeEncoding.INVALID_INSTANT) {
                // no data retrieved from archive, but maybe there is still something in the cache to send
                sendFromCache(pid, pcache, false, mpvr.getStart(), mpvr.getStop(), consumer);
            }
        }
    }

    // send data from cache with timestamps in (start, stop) if ascending or (start, stop] if descending interval
    private void sendFromCache(ParameterWithId pid, ParameterCache pcache, boolean ascending, long start,
            long stop, Consumer<ParameterValueWithId> consumer) {
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
                    sendToConsumer(pv, pid, consumer);
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
                sendToConsumer(pv, pid, consumer);
            }
        }
    }

    private void sendToConsumer(ParameterValue pv, ParameterWithId pid,
            Consumer<ParameterValueWithId> consumer) {
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
        consumer.accept(new ParameterValueWithId(pv1, pid.getId()));
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

        org.yamcs.protobuf.Pvalue.ParameterStatus[] paramStatus = null;

        if (opts.retrieveParameterStatus()) {
            paramStatus = new org.yamcs.protobuf.Pvalue.ParameterStatus[] {
                    pv.getStatus().toProtoBuf() };
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
    }
}
