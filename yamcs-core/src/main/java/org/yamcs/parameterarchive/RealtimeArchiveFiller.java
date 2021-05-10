package org.yamcs.parameterarchive;

import static org.yamcs.parameterarchive.ParameterArchive.getInterval;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.rocksdb.RocksDBException;
import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.Spec.OptionType;
import org.yamcs.logging.Log;
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
    protected final ParameterIdDb parameterIdMap;
    protected final ParameterGroupIdDb parameterGroupIdMap;
    final ParameterArchive parameterArchive;
    final private Log log;
    ExecutorService executor;
    Map<Integer, SegmentQueue> queues = new HashMap<>();

    // int flushInterval; // seconds

    // max allowed time for old data
    long sortingThreshold;

    // reset the processing if time jumps in the past by this much
    long pastJumpThreshold;

    int numThreads;

    public RealtimeArchiveFiller(ParameterArchive parameterArchive, YConfiguration config) {
        super(parameterArchive);
        this.parameterArchive = parameterArchive;
        this.parameterIdMap = parameterArchive.getParameterIdDb();
        this.parameterGroupIdMap = parameterArchive.getParameterGroupIdDb();
        this.yamcsInstance = parameterArchive.getYamcsInstance();
        log = new Log(this.getClass(), yamcsInstance);

        // flushInterval = config.getInt("flushInterval", 300);
        processorName = config.getString("processorName", processorName);
        sortingThreshold = config.getInt("sortingThreshold");
        numThreads = config.getInt("numThreads", getDefaultNumThreads());
        pastJumpThreshold = config.getLong("pastJumpThreshold") * 1000;
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

        return spec;
    }

    protected void start() {
        // subscribe to the realtime processor
        realtimeProcessor = YamcsServer.getServer().getProcessor(yamcsInstance, processorName);
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
    }

    public void shutDown() throws InterruptedException {
        realtimeProcessor.getParameterRequestManager().unsubscribeAll(subscriptionId);

        log.info("Shutting down, writing all pending segments");
        for (SegmentQueue queue : queues.values()) {
            queue.flush();
        }
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            log.warn("Timedout before flusing all pending segments");
        }
    }

    @Override
    protected void processParameters(long t, BasicParameterList pvList) {
        try {
            int parameterGroupId = parameterGroupIdMap.createAndGet(pvList.getPids());

            SegmentQueue segQueue = queues.computeIfAbsent(parameterGroupId,
                    id -> new SegmentQueue(parameterGroupId, pvList.getPids()));
            segQueue.addRecord(t, pvList.getValues());
        } catch (RocksDBException e) {
            log.error("Error writing to the parameter archive", e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void abort() {
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
    class SegmentQueue {
        static final int QSIZE = 16; // has to be a power of 2!
        static final int MASK = QSIZE - 1;
        final PGSegment[] segments = new PGSegment[QSIZE];
        int head = 0;
        int tail = 0;

        final int parameterGroupId;
        final IntArray parameterIds;

        public SegmentQueue(int parameterGroupId, IntArray parameterIds) {
            this.parameterGroupId = parameterGroupId;
            this.parameterIds = parameterIds;
        }

        public synchronized void addRecord(long t, List<BasicParameterValue> values) {
            if (head != tail) {
                PGSegment segHead = segments[head];

                if (t < segHead.getSegmentStart() - pastJumpThreshold) {
                    log.warn("Time jumped in the past; current timestamp: {}, new timestamp: {}. Flushing old data.",
                            TimeEncoding.toString(segHead.getSegmentStart()), TimeEncoding.toString(t));
                    flush();
                } else if (t < segHead.getSegmentStart()) {
                    log.warn("Dropping old data with timestamp {} (minimum allowed is {})."
                            + "Unsorted data received in the realtime filler? Consider using a backfiller instead",
                            TimeEncoding.toString(t),
                            TimeEncoding.toString(segments[head].getSegmentStart() - sortingThreshold));
                    return;
                } else {
                    writeToArchive(t);
                }
            }

            boolean added = false;
            int k = head;
            long tintv = getInterval(t);

            for (; k != tail; k = (k + 1) & MASK) {
                PGSegment seg = segments[k];
                long kintv = seg.getInterval();
                if (kintv < tintv) {
                    continue;
                } else if (kintv > tintv) {
                    break;
                }
                assert (t >= seg.getSegmentStart());

                if (t <= seg.getSegmentEnd() || seg.size() < maxSegmentSize) {
                    // when the first condition is met only, the segment will become bigger than the maxSegmentSize
                    // could be split in two
                    seg.addRecord(t, values);
                    added = true;
                    break;
                }
            }

            if (!added) {// to be added on position k
                if (segments[tail] != null) {
                    log.warn("Realtime parameter archive queue full."
                            + "Consider increasing the writerThreads (if CPUs are available) or using a back filler");
                    return;
                }

                long segStart = t - sortingThreshold;
                PGSegment prev = segments[k - 1 & MASK];
                if (prev != null) {
                    if (segStart <= prev.getSegmentEnd()) {
                        segStart = prev.getSegmentEnd() + 1;
                    }
                }
                if (segStart < tintv) {
                    segStart = tintv;
                }
                PGSegment seg = new PGSegment(parameterGroupId, segStart, parameterIds);
                seg.addRecord(t, values);

                // shift everything between k and tail to the right
                for (int i = k; i < tail; i = (i + 1) & MASK) {
                    segments[(i + 1) & MASK] = segments[i];
                }
                tail = (tail + 1) & MASK;

                // insert on position k
                segments[k] = seg;

            }
        }

        /**
         * write to archive all segments that cannot get new data.
         * <p>
         * t is the timestamp of the newest received data, we won't accept data older than t1=t-sortingThrreshold
         * <p>
         * it means that all segments which are either from an older interval than t1 or are full and their end is
         * smaller than t1, can be written
         */
        private void writeToArchive(long t) {
            long t1 = t - sortingThreshold;
            while (head != tail) {
                PGSegment seg = segments[head];

                if (seg.getInterval() >= getInterval(t1)
                        && (seg.size() < maxSegmentSize || seg.getSegmentEnd() >= t1)) {
                    break;
                }
                int _head = head;
                head = (head + 1) & MASK;
                sendToExecutor(_head);
            }
        }

        public synchronized void flush() {
            while (head != tail) {
                sendToExecutor(head);
                head = (head + 1) & MASK;
            }
        }

        private void sendToExecutor(int idx) {
            executor.submit(() -> {
                try {
                    PGSegment seg = segments[idx];
                    long t0 = System.nanoTime();
                    parameterArchive.writeToArchive(seg);
                    long d = System.nanoTime() - t0;
                    log.debug("Wrote segment {} to archive in {} millisec", seg, d / 1000_000);
                } catch (RocksDBException | IOException e) {
                    log.error("Error writing segment to the parameter archive", e);
                }
                segments[idx] = null;
            });
        }

        public int size() {
            return (tail - head) & MASK;
        }

        public boolean isEmpty() {
            return head == tail;
        }

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
            while (k != tail && segments[(k - 1) & MASK] != null) {
                k = (k - 1) & MASK;
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
                k = (k + 1) & MASK;
            }

            return r;
        }

        private List<ParameterValueSegment> getSegmentsDescending(int pid) {
            List<ParameterValueSegment> r = new ArrayList<>();

            int k = (tail - 1) & MASK;

            while (true) {
                PGSegment seg = segments[k];
                if (seg == null) {
                    break;
                }
                ParameterValueSegment pvs = seg.getParameterValue(pid);
                if (pvs != null) {
                    r.add(pvs);
                }
                k = (k - 1) & MASK;
            }

            return r;
        }
    }
}
