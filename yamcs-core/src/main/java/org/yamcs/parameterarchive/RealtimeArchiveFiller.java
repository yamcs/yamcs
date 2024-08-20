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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.rocksdb.RocksDBException;
import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.Spec.OptionType;
import org.yamcs.parameterarchive.ParameterGroupIdDb.ParameterGroup;
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
    int flushInterval = 60; // seconds

    // max allowed time for old data
    long sortingThreshold;// milliseconds

    // reset the processing if time jumps in the past by this much
    long pastJumpThreshold;

    int numThreads;

    public RealtimeArchiveFiller(ParameterArchive parameterArchive, YConfiguration config) {
        super(parameterArchive);
        this.yamcsInstance = parameterArchive.getYamcsInstance();

        flushInterval = config.getInt("flushInterval", 60);
        processorName = config.getString("processorName", processorName);
        sortingThreshold = config.getInt("sortingThreshold");
        numThreads = config.getInt("numThreads", getDefaultNumThreads());
        pastJumpThreshold = config.getLong("pastJumpThreshold") * 1000;
        if (flushInterval * 1000 < sortingThreshold) {
            throw new ConfigurationException("flushInterval (" + flushInterval
                    + " seconds) cannot be smaller than the sorting threshold (" + sortingThreshold + " milliseconds)");
        }
    }

    static Spec getSpec() {
        Spec spec = new Spec();

        spec.addOption("enabled", OptionType.BOOLEAN);
        spec.addOption("processorName", OptionType.STRING).withDefault("realtime");
        spec.addOption("sortingThreshold", OptionType.INTEGER).withDefault(1000);
        spec.addOption("numThreads", OptionType.INTEGER);
        spec.addOption("pastJumpThreshold", OptionType.INTEGER)
                .withDescription("When receiving data with an old timestamp differing from the previous data "
                        + "by more than this threshold in seconds, the old segments are flushed to archinve and a new one is started. "
                        + "This is to avoid that the data is rejected because the time is reinitialized on-board for example.")
                .withDefault(86400);
        spec.addOption("flushInterval", OptionType.INTEGER).withDescription(
                "If no data is received for a parameter group in this number of seconds, then flush the data to disk. "
                        + "If data is received, the data will be flushed after maxSegmentSize data points are received")
                .withDefault(60);
        return spec;
    }

    /**
     * Gets the Yamcs server reference. Code in this class should call this method rather than
     * <code>YamcsServer.getServer()</code> so the server can be mocked for unit testing.
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
     * Sets the Yamcs server to use. Default scope for unit testing. Should only be called by unit tests.
     * 
     * @param yamcsServer
     *            the Yamcs server to use, perhaps a mock object
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
                if (!queue.isEmpty() && now > queue.getLatestUpdateTime() + flushInterval * 1000L) {
                    log.debug("Flush interval reached without new data for parameter group {}, flushing queue",
                            queueEntry.getKey());
                    queue.flush();
                }
            }
        }
    }

    public void shutDown() throws InterruptedException {
        realtimeProcessor.getParameterRequestManager().unsubscribeAll(subscriptionId);
        log.info("Shutting down, writing all pending segments");
        for (SegmentQueue queue : queues.values()) {
            queue.flush();
        }
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            log.warn("Timed out before flushing all pending segments");
        }
    }

    @Override
    protected void processParameters(long t, BasicParameterList pvList) {
        ParameterGroup pg;
        try {
            pg = parameterGroupIdMap.getGroup(pvList.getPids());
        } catch (RocksDBException e) {
            log.error("Error creating parameter group id", e);
            return;
        }

        SegmentQueue segQueue = queues.computeIfAbsent(pg.id,
                id -> new SegmentQueue(pg.id, maxSegmentSize, pgs -> scheduleWriteToArchive(pgs),
                        interval -> readPgSegment(pg, interval)));

        synchronized (segQueue) {
            if (!segQueue.isEmpty()) {
                long segStart = segQueue.getStart();
                if (t < segStart - pastJumpThreshold) {
                    log.warn(
                            "Time jumped in the past; current timestamp: {}, new timestamp: {}. Flushing old data.",
                            TimeEncoding.toString(segStart), TimeEncoding.toString(t));
                    segQueue.flush();
                } else if (t < segStart - sortingThreshold) {
                    log.warn("Dropping old data with timestamp {} (minimum allowed is {})."
                            + "Unsorted data received in the realtime filler? Consider using a backfiller instead",
                            TimeEncoding.toString(t),
                            TimeEncoding.toString(segStart - sortingThreshold));
                    return;
                }
            }

            if (segQueue.addRecord(t, pvList)) {
                segQueue.sendToArchive(t - sortingThreshold);
            } else {
                log.warn("Realtime parameter archive queue full."
                        + "Consider increasing the writerThreads (if CPUs are available) or using a back filler");
            }
        }

    }

    private PGSegment readPgSegment(ParameterGroup pg, long interval) {
        try {
            return parameterArchive.readPGsegment(pg, interval);
        } catch (IOException | RocksDBException e) {
            log.error("Error reading old data from archive", e);
            return null;
        }
    }

    private CompletableFuture<Void> scheduleWriteToArchive(PGSegment pgs) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        try {
            executor.submit(() -> {
                doWriteToArchive(pgs, cf);
            });
        } catch (RejectedExecutionException e) {
            // the executor is shutdown and won't accept new tasks, keep writing in the same thread
            doWriteToArchive(pgs, cf);
        }
        return cf;
    }

    private void doWriteToArchive(PGSegment pgs, CompletableFuture<Void> cf) {
        try {
            long t0 = System.nanoTime();
            parameterArchive.writeToArchive(pgs);
            long d = System.nanoTime() - t0;
            log.debug("Wrote segment {} to archive in {} millisec", pgs, d / 1000_000);
            cf.complete(null);
        } catch (RocksDBException | IOException e) {
            log.error("Error writing segment to the parameter archive", e);
            cf.completeExceptionally(e);
        }
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
     * The segments are references to the data that is being added, that means they are modified by external threads.
     * <p>
     * Some segments may just being written to the archive, so care has to be taken by the caller to eliminate duplicate
     * data when using the return of this method combined with reading data from archive. The {@link SegmentIterator}
     * does that.
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
     * Works like a queue, new segments are added to the tail, they are written to the archive from the head. The
     * elements in the queue are only cleared (set to null) after they have been written to the archive, even if
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
     */
    static class SegmentQueue {
        static final int QSIZE = 16; // has to be a power of 2!
        static final int MASK = QSIZE - 1;
        final PGSegment[] segments = new PGSegment[QSIZE];
        int head = 0;
        int tail = 0;

        final int parameterGroupId;
        final int maxSegmentSize;

        private long latestUpdateTime;

        // this function is used to write to the archive
        // it returns a completable future which is completed when the data has been written.
        final Function<PGSegment, CompletableFuture<Void>> writeToArchiveFunction;

        // used to read an existing segment from the archive at startup or if after a period of inactivity the data has
        // been flushed
        final Function<Long, PGSegment> readFromArchiveFunction;

        // we use this to make sure that only one write is running for a given pg at a time
        CompletableFuture<Void> lastWriteFuture = CompletableFuture.completedFuture(null);

        public SegmentQueue(int parameterGroupId, int maxSegmentSize,
                Function<PGSegment, CompletableFuture<Void>> writeToArchiveFunction,
                Function<Long, PGSegment> readFromArchiveFunction) {
            this.parameterGroupId = parameterGroupId;
            this.maxSegmentSize = maxSegmentSize;
            this.writeToArchiveFunction = writeToArchiveFunction;
            this.readFromArchiveFunction = readFromArchiveFunction;
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
         * Given the queue state s1, s2, s3... sn, it is inserted into the sk such that the t is in the same interval as
         * sk and either sk is not full, or t<sk.end.
         * <p>
         * If not such a segment exists, a new segment is created and inserted in the queue (if the queue is not full).
         * <p>
         * Returns true if the record has been added or false if the queue was full.
         */
        public synchronized boolean addRecord(long t, BasicParameterList pvList) {
            latestUpdateTime = System.currentTimeMillis();

            int k = head;
            long tintv = getInterval(t);

            if (isEmpty()) {
                // we need to read the existing interval from the archive, we may need to add to it
                // or in any case to continue it
                PGSegment prevSeg = readFromArchiveFunction.apply(tintv);
                if (prevSeg != null && t <= prevSeg.getSegmentEnd()) {
                    // data fits into the previous segment
                    prevSeg.makeWritable();
                    prevSeg.addRecord(t, pvList);
                    segments[tail] = prevSeg;
                    tail = inc(tail);
                    return true;
                }
                // else we make a new segment continuing the previous one (if it exists)
                var pids = pvList.getPids();
                PGSegment seg = new PGSegment(parameterGroupId, ParameterArchive.getInterval(t), pids.size());
                seg.addRecord(t, pvList);
                if (prevSeg != null) {
                    prevSeg.freeze();
                    seg.continueSegment(prevSeg);
                }
                segments[tail] = seg;
                tail = inc(tail);
                return true;
            }

            // SeqmentQueue not empty, look for a segment where to place the new data
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
                    seg.addRecord(t, pvList);
                    return true;
                }
            }

            // new segment to be added on position k
            // If there is only one slot free, then the queue is already full.
            // if segments[tail] is not null, it means it hasn't been written to the archive yet (async operation),
            // we do not want to overwrite it because it won't be found in the retrieval
            if (inc(tail) == head || segments[tail] != null) {
                return false;
            }

            var pids = pvList.getPids();
            PGSegment seg = new PGSegment(parameterGroupId, ParameterArchive.getInterval(t), pids.size());
            seg.addRecord(t, pvList);
            // shift everything between k and tail to the right
            for (int i = k; i != tail; i = inc(i)) {
                segments[inc(i)] = segments[i];
            }
            tail = inc(tail);

            // insert on position k
            segments[k] = seg;
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
        void sendToArchive(long t1) {
            while (head != tail) {
                PGSegment seg = segments[head];

                if (seg.getInterval() >= getInterval(t1)
                        && (seg.size() < maxSegmentSize || seg.getSegmentEnd() >= t1)) {
                    break;
                }
                sendHeadToArchive();
            }
        }

        synchronized void flush() {
            while (head != tail) {
                sendHeadToArchive();
            }
        }

        // send the head to the archive and move the head towards the tail
        private void sendHeadToArchive() {
            PGSegment seg = segments[head];
            seg.freeze();

            int _head = head;
            head = inc(head);
            if (head != tail) {
                var nextSeg = segments[head];
                if (nextSeg.getInterval() == seg.getInterval()) {
                    nextSeg.continueSegment(seg);
                }
            }
            toArchive(_head);
        }

        private void toArchive(int idx) {
            PGSegment seg = segments[idx];
            lastWriteFuture = lastWriteFuture
                    .thenCompose(v -> writeToArchiveFunction.apply(seg))
                    .thenAccept(v -> segments[idx] = null);
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
            if (isEmpty()) {
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
