package org.yamcs.parameterarchive;

import static org.yamcs.parameterarchive.ParameterArchive.getInterval;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.rocksdb.RocksDBException;
import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.Spec.OptionType;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Realtime archive filler task - it works even if the data is not perfectly sorted
 * <p>
 * It can save data in max two intervals at a time. The first interval is kept open only as long as the most recent
 * timestamp received is not older than orderingThreshold ms from the interval end
 * <p>
 * 
 * When new parameters are received, they are sorted into groups with all parameter from the same group having the same
 * timestamp.
 * 
 * <p>
 * Max two segments are kept open for each group, one in each interval.
 * 
 * <p>
 * If the group reaches its max size, it is archived and a new one opened.
 * 
 * @author nm
 *
 */
public class RealtimeArchiveFiller extends AbstractArchiveFiller {

    String processorName = "realtime";
    final String yamcsInstance;
    Processor realtimeProcessor;
    int subscriptionId;
    ExecutorService executor;
    Map<Integer, SegmentQueue> queues = new HashMap<>();
    private YamcsServer yamcsServer;

    // Maximum time to wait for new data before flushing to archive
    int flushInterval; // seconds

    // max allowed time for old data
    long sortingThreshold;

    // reset the processing if time jumps in the past by this much
    long pastJumpThreshold;

    int numThreads;

    public RealtimeArchiveFiller(ParameterArchive parameterArchive, YConfiguration config) {
        super(parameterArchive);
        this.yamcsInstance = parameterArchive.getYamcsInstance();

        flushInterval = config.getInt("flushInterval", 300);
        processorName = config.getString("processorName", processorName);
        sortingThreshold = config.getInt("sortingThreshold");
        numThreads = config.getInt("numThreads", getDefaultNumThreads());
        pastJumpThreshold = config.getLong("pastJumpThreshold") * 1000;
    }

    static Spec getSpec() {
        Spec spec = new Spec();

        spec.addOption("flushInterval", OptionType.INTEGER).withDefault(300);
        spec.addOption("enabled", OptionType.BOOLEAN);
        spec.addOption("processorName", OptionType.STRING).withDefault("realtime");
        spec.addOption("sortingThreshold", OptionType.INTEGER).withDefault(1000);
        spec.addOption("numThreads", OptionType.INTEGER);
        spec.addOption("pastJumpThreshold", OptionType.INTEGER)
                .withDescription("When receiving data with an old timestamp differing from the previous data "
                        + "by more than this threshold in seconds, the old segments are flushed to archinve and a new one is started. "
                        + "This is to avoid that the data is rejected because the time is reinitialized on-board for example.")
                .withDefault(86400);

        return spec;
    }
    
    /**
     * Gets the Yamcs server reference. Code in this class should call this method
     * rather than <code>YamcsServer.getServer()</code> so the server can be mocked
     * for unit testing.
     * 
     * @return the Yamcs server reference
     */
    private synchronized YamcsServer getYamcsServer() {
        if (yamcsServer == null) {
            yamcsServer = YamcsServer.getServer();
        }
        return yamcsServer;
    }
    
    /**
     * Sets the Yamcs server to use. Default scope for unit testing. Should only
     * be called by unit tests.
     * 
     * @param yamcsServer the Yamcs server to use, perhaps a mock object
     */
    synchronized void setYamcsServer(YamcsServer yamcsServer) {
        this.yamcsServer = yamcsServer;
    }

    protected void start() {
        // subscribe to the realtime processor
        realtimeProcessor = getYamcsServer().getProcessor(yamcsInstance, processorName);
        if (realtimeProcessor == null) {
            throw new ConfigurationException("No processor named '" + processorName + "' in instance " + yamcsInstance);
        }
        if (realtimeProcessor.getParameterCache() != null) {
            log.warn("Both realtime archive filler and parameter cache configured for processor {}."
                    + "The parameter cache can be safely disabled (to save memory) by setting parameterCache->enabled "
                    + "to false in processor.yaml",
                    processorName);
        }
        subscriptionId = realtimeProcessor.getParameterRequestManager().subscribeAll(this);

        log.debug("Starting executor for archive writing with {} threads", numThreads);
        executor = Executors.newFixedThreadPool(numThreads,
                new ThreadFactoryBuilder().setNameFormat("realtime-parameter-archive-writer-%d").build());

        var timer = getYamcsServer().getThreadPoolExecutor();
        if (timer != null) {
            timer.scheduleAtFixedRate(this::flushPeriodically, flushInterval, flushInterval, TimeUnit.SECONDS);
        }
    }

    private void flushPeriodically() {
        long now = System.currentTimeMillis();
        for (var queueEntry : queues.entrySet()) {
            SegmentQueue queue = queueEntry.getValue();
            synchronized (queue) {
                if (queue.isEmpty() && now > queue.getLatestUpdateTime() + flushInterval * 1000L) {
                    queue.flush(this::scheduleWriteToArchive);
                    log.debug("Flush interval reached without new segment for parameter group {}, flushing queue",
                            queueEntry.getKey());
                }
            }
        }
    }

    public void shutDown() throws InterruptedException {
        realtimeProcessor.getParameterRequestManager().unsubscribeAll(subscriptionId);

        log.info("Shutting down, writing all pending segments");
        for (SegmentQueue queue : queues.values()) {
            queue.flush(pgs -> scheduleWriteToArchive(pgs));
        }
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            log.warn("Timed out before flushing all pending segments");
        }
    }

    @Override
    protected void processParameters(long t, BasicParameterList pvList) {
        int parameterGroupId;
        try {
            parameterGroupId = parameterGroupIdMap.createAndGet(pvList.getPids());
        } catch (RocksDBException e) {
            log.error("Error creating parameter group id", e);
            return;
        }

        SegmentQueue segQueue = queues.computeIfAbsent(parameterGroupId,
                id -> new SegmentQueue(parameterGroupId, pvList.getPids(), maxSegmentSize));

        synchronized (segQueue) {
            if (!segQueue.isEmpty()) {
                long segStart = segQueue.getStart();
                if (t < segStart - pastJumpThreshold) {
                    log.warn(
                            "Time jumped in the past; current timestamp: {}, new timestamp: {}. Flushing old data.",
                            TimeEncoding.toString(segStart), TimeEncoding.toString(t));
                    segQueue.flush(pgs -> scheduleWriteToArchive(pgs));
                } else if (t < segStart - sortingThreshold) {
                    log.warn("Dropping old data with timestamp {} (minimum allowed is {})."
                            + "Unsorted data received in the realtime filler? Consider using a backfiller instead",
                            TimeEncoding.toString(t),
                            TimeEncoding.toString(segStart - sortingThreshold));
                    return;
                } else {
                    segQueue.sendToArchive(t - sortingThreshold, pgs -> scheduleWriteToArchive(pgs));
                }
            }

            if (!segQueue.addRecord(t, pvList.getValues())) {
                log.warn("Realtime parameter archive queue full."
                        + "Consider increasing the writerThreads (if CPUs are available) or using a back filler");
            }
        }

    }

    private CompletableFuture<Void> scheduleWriteToArchive(PGSegment pgs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long t0 = System.nanoTime();
                parameterArchive.writeToArchive(pgs);
                long d = System.nanoTime() - t0;
                log.debug("Wrote segment {} to archive in {} millisec", pgs, d / 1000_000);
            } catch (RocksDBException | IOException e) {
                log.error("Error writing segment to the parameter archive", e);
            }
            return null;
        }, executor);
    }

    /**
     * Called when risking running out of memory, drop all data
     */
    @Override
    protected void abort() {
        queues.clear();
    }

    private int getDefaultNumThreads() {
        int n = Runtime.getRuntime().availableProcessors() - 1;
        return n > 0 ? n : 1;
    }

    /**
     * Return the list of segments for the (parameterId, parameterGroupId) currently in memory. If there is no data, an
     * empty list is returned.
     * <p>
     * If ascending is false, the list of segments is sorted by descending start time but the data inside the segments
     * is still sorted in ascending order.
     * <p>
     * The segments are references to the data that is being added, that means they are modified by external
     * threads.
     * <p>
     * Some segments may just being written to the archive, so care has to be taken by the caller to eliminate duplicate
     * data when using the return of this method combined with reading data from archive.
     * The {@link SegmentIterator} does that.
     * 
     * 
     * @param parameterId
     * @param parameterGroupId
     * @param ascending
     * @return
     */
    public List<ParameterValueSegment> getSegments(int parameterId, int parameterGroupId, boolean ascending) {
        SegmentQueue queue = queues.get(parameterGroupId);
        if (queue == null) {
            return Collections.emptyList();
        }

        return queue.getPVSegments(parameterId, ascending);
    }

    public List<MultiParameterValueSegment> getSegments(ParameterId[] pids, int parameterGroupId, boolean ascending) {
        SegmentQueue queue = queues.get(parameterGroupId);
        if (queue == null) {
            return Collections.emptyList();
        }

        return queue.getPVSegments(pids, ascending);
    }
    /**
     * 
     * This class is used to accumulate "slightly" unsorted data and also keeps the data while is being written to the
     * archive.
     * <p>
     * Works like a queue, new segments are added to the tail, they are written to the archive from the head.
     * The elements in the queue are only cleared (set to null) after they have been written to the archive, even if
     * theoretically they are out of the queue.
     * <p>
     * This gives the chance to still use the data in the retrieval. See {@link SingleParameterRetrieval} and
     * {@link MultiParameterRetrieval}
     * 
     * <p>
     * theoretically if the data comes at the high frequency and the sortingThreshold is high, we can accumulate lots of
     * segments in memory. There is however a limit of 16 hardcoded for now.
     * <p>
     * Sometimes the maxSegmentSize is exceeded because if a segment is full and new unsorted data fits inside, it is
     * still added.
     * 
     * <p>
     * 
     *
     */
    static class SegmentQueue {
        static final int QSIZE = 16; // has to be a power of 2!
        static final int MASK = QSIZE - 1;
        final PGSegment[] segments = new PGSegment[QSIZE];
        int head = 0;
        int tail = 0;

        final int parameterGroupId;
        final IntArray parameterIds;
        final int maxSegmentSize;

        private long latestUpdateTime;

        public SegmentQueue(int parameterGroupId, IntArray parameterIds, int maxSegmentSize) {
            this.parameterGroupId = parameterGroupId;
            this.parameterIds = parameterIds;
            this.maxSegmentSize = maxSegmentSize;
        }

        public long getStart() {
            if (isEmpty()) {
                throw new IllegalStateException("queue is empty");
            }
            return segments[head].getSegmentStart();
        }

        /**
         * Add the record to the queue.
         * <p>
         * Given the queue state s1, s2, s3... sn, it is inserted into the sk such that the
         * t is in the same interval as sk and either sk is not full, or t<sk.end.
         * <p>
         * If not such a segment exists, a new segment is created and inserted in the queue (if the queue is not full).
         * <p>
         * Returns true if the record has been added or false if the queue was full.
         */
        public synchronized boolean addRecord(long t, List<BasicParameterValue> values) {
            latestUpdateTime = System.currentTimeMillis();

            boolean added = false;
            int k = head;
            long tintv = getInterval(t);

            for (; k != tail; k = inc(k)) {
                PGSegment seg = segments[k];
                long kintv = seg.getInterval();
                if (kintv < tintv) {
                    continue;
                } else if (kintv > tintv) {
                    break;
                }

                if (t <= seg.getSegmentEnd() || seg.size() < maxSegmentSize) {
                    // when the first condition is met only (i.e. new data coming in the middle of a full segment)
                    // the segment will become bigger than the maxSegmentSize
                    // FIXME: split the interval in two if this happens
                    seg.addRecord(t, values);
                    added = true;
                    break;
                }
            }

            if (!added) {// new segment to be added on position k
                // If there is only one slot free, then the queue is already full.
                // if segments[tail] is not null, it means it hasn't been written to the archive yet (async operation),
                // we do not want to overwrite it because it won't be found in the retrieval
                if (inc(tail) == head || segments[tail] != null) {
                    return false;
                }

                PGSegment seg = new PGSegment(parameterGroupId, t, parameterIds);
                seg.addRecord(t, values);

                // shift everything between k and tail to the right
                for (int i = k; i != tail; i = inc(i)) {
                    segments[inc(i)] = segments[i];
                }
                tail = inc(tail);

                // insert on position k
                segments[k] = seg;

            }
            return true;
        }



        /**
         * send to archive all segments which are either from an older interval than t1 or are full and their end is
         * smaller than t1.
         * <p>
         * Writing to archive is an async operation, and the completable future returned by the function is called when
         * the writing to archive has been completed and is used to null the entry in the queue. Before the entry is
         * null, the data can still be used in the retrieval.
         */
        void sendToArchive(long t1, Function<PGSegment, CompletableFuture<Void>> f) {
            while (head != tail) {
                PGSegment seg = segments[head];

                if (seg.getInterval() >= getInterval(t1)
                        && (seg.size() < maxSegmentSize || seg.getSegmentEnd() >= t1)) {
                    break;
                }
                int _head = head;
                head = inc(head);
                toArchive(_head, f);
            }
        }

        synchronized void flush(Function<PGSegment, CompletableFuture<Void>> f) {
            while (head != tail) {
                toArchive(head, f);
                head = inc(head);
            }
        }

        private void toArchive(int idx, Function<PGSegment, CompletableFuture<Void>> f) {
            PGSegment seg = segments[idx];

            f.apply(seg).thenAccept(v -> {
                segments[idx] = null;
            });
        }

        public int size() {
            return (tail - head) & MASK;
        }

        public boolean isEmpty() {
            return head == tail;
        }

        /**
         * Returns a list of segments for the pid.
         * 
         * <p>
         * The ascending argument can be used to sort the segments in ascending or descending order. The values inside
         * the segments will always be ascending (but one can iterate the segment in descending order).
         * 
         */
        public synchronized List<ParameterValueSegment> getPVSegments(int pid, boolean ascending) {
            if (head == tail) {
                return Collections.emptyList();
            }

            if (ascending) {
                return getSegmentsAscending(pid);
            } else {
                return getSegmentsDescending(pid);
            }
        }

        private List<ParameterValueSegment> getSegmentsAscending(int pid) {
            List<ParameterValueSegment> r = new ArrayList<>();

            int k = head;
            while (k != tail && segments[dec(k)] != null) {
                k = dec(k);
            }

            while (k != tail) {
                PGSegment seg = segments[k];
                if (seg == null) {
                    continue;
                }

                ParameterValueSegment pvs = seg.getParameterValue(pid);
                if (pvs != null) {
                    r.add(pvs);
                }
                k = inc(k);
            }

            return r;
        }

        private List<ParameterValueSegment> getSegmentsDescending(int pid) {
            List<ParameterValueSegment> r = new ArrayList<>();

            int k = dec(tail);

            while (true) {
                PGSegment seg = segments[k];
                if (seg == null) {
                    break;
                }
                ParameterValueSegment pvs = seg.getParameterValue(pid);
                if (pvs != null) {
                    r.add(pvs);
                }
                k = dec(k);
            }

            return r;
        }

        /**
         * Returns a list of segments for the pid.
         * 
         * <p>
         * The ascending argument can be used to sort the segments in ascending or descending order. The values inside
         * the segments will always be ascending (but one can iterate the segment in descending order).
         * 
         */
        public synchronized List<MultiParameterValueSegment> getPVSegments(ParameterId[] pids, boolean ascending) {
            if (head == tail) {
                return Collections.emptyList();
            }

            if (ascending) {
                return getSegmentsAscending(pids);
            } else {
                return getSegmentsDescending(pids);
            }
        }

        private List<MultiParameterValueSegment> getSegmentsAscending(ParameterId[] pids) {
            List<MultiParameterValueSegment> r = new ArrayList<>();

            int k = head;
            while (k != tail && segments[dec(k)] != null) {
                k = dec(k);
            }

            while (k != tail) {
                PGSegment seg = segments[k];
                if (seg == null) {
                    continue;
                }

                MultiParameterValueSegment pvs = seg.getParametersValues(pids);
                if (pvs != null) {
                    r.add(pvs);
                }
                k = inc(k);
            }

            return r;
        }

        private List<MultiParameterValueSegment> getSegmentsDescending(ParameterId[] pids) {
            List<MultiParameterValueSegment> r = new ArrayList<>();

            int k = dec(tail);

            while (true) {
                PGSegment seg = segments[k];
                if (seg == null) {
                    break;
                }
                MultiParameterValueSegment pvs = seg.getParametersValues(pids);
                if (pvs != null) {
                    r.add(pvs);
                }
                k = dec(k);
            }

            return r;
        }

        /**
         * Circularly increment k
         */
        static final int inc(int k) {
            return (k + 1) & MASK;
        }

        /**
         * Circularly decrement k
         */
        static final int dec(int k) {
            return (k - 1) & MASK;
        }

        public long getLatestUpdateTime() {
            return latestUpdateTime;
        }
    }

}
