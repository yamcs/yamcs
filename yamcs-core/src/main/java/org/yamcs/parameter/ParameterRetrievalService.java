package org.yamcs.parameter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import org.rocksdb.RocksDBException;
import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.http.api.ParameterReplayListener;
import org.yamcs.parameterarchive.ConsumerAbortException;
import org.yamcs.parameterarchive.MultiParameterRetrieval;
import org.yamcs.parameterarchive.MultipleParameterRequest;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.parameterarchive.ParameterId;
import org.yamcs.parameterarchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterIdValueList;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.SingleParameterRetrieval;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.MutableLong;
import org.yamcs.utils.TimeEncoding;

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
    ParameterCache pcache;
    ParameterArchive parchive;
    ExecutorService executor;
    
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        this.procName = config.getString("processor", DEFAULT_PROCESSOR);
        
        int parallelRetrievals = config.getInt("parallelRetrievals", 2);
        this.executor = createExecutor(parallelRetrievals);
    }

   

    @Override
    protected void doStart() {
        var ysi = YamcsServer.getServer().getInstance(yamcsInstance);
        var proc = ysi.getProcessor(procName);
        pcache = new ArrayParameterCache(yamcsInstance, null);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        executor.shutdown();
        notifyStopped();
    }

    // Called from the PRM
    public void update(List<ParameterValue> pvlist) {
        if (pcache != null) {
            pcache.update(pvlist);
        }
    }

    /**
     * Retrieves a single scalar parameter or aggregate/array member.
     */
    public void retrieveScalar(ParameterWithId pid, ParameterRetrievalOptions opts, Consumer<ParameterValueArray> consumer)
            throws IOException {
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

    TimeAndCount retrieveScalarReplayOrCache(ParameterWithId pid, ParameterRetrievalOptions request,
            Consumer<ParameterValueArray> consumer) {
        return null;
    }

    public TimeAndCount retrieveScalarParameterArchive(ParameterWithId pid, ParameterRetrievalOptions request,
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

    static final class TimeAndCount {
        long time;
        int count;

        public TimeAndCount(long time, int count) {
            this.time = time;
            this.count = count;
        }
    }


    public void retrieve(ParameterWithId requestedParamWithId, ParameterRetrievalOptions opts,
            ParameterReplayListener replayListener) {
        MultipleParameterRequest mpvr;
        ParameterIdDb piddb = parchive.getParameterIdDb();
        String qn = requestedParamWithId.getQualifiedName();
        ParameterId[] pids = piddb.get(qn);
        if (pids != null) {
            mpvr = new MultipleParameterRequest(opts.start(), opts.stop(), pids, opts.ascending());
        } else {
            log.debug("No parameter id found in the parameter archive for {}", qn);
            mpvr = null;
        }

        // do not use set limit because the data can be filtered down (e.g. noRepeat) and the limit applies the final
        // filtered data not to the input
        // one day the parameter archive will be smarter and do the filtering inside
        // mpvr.setLimit(limit);

        ParameterCache pcache = null;


    };

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
}
