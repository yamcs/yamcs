package org.yamcs.parameterarchive;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.yamcs.Processor;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.TimeEncoding;

/**
 * Archive filler that creates segments of max size .
 * 
 * @author nm
 *
 */
class ArchiveFillerTask implements ParameterConsumer {
    final ParameterArchive parameterArchive;
    final private Log log;

    long numParams = 0;
    static int DEFAULT_MAX_SEGMENT_SIZE = 5000;
    static MemoryPoolMXBean memoryBean = getMemoryBean();
    // ParameterGroup_id -> PGSegment
    protected Map<Integer, PGSegment> pgSegments = new HashMap<>();
    protected final ParameterIdDb parameterIdMap;
    protected final ParameterGroupIdDb parameterGroupIdMap;

    // ignore any data older than this
    protected long collectionSegmentStart;

    long threshold = 60000;
    int maxSegmentSize;
    private Processor processor;
    boolean aborted = false;

    public ArchiveFillerTask(ParameterArchive parameterArchive, int maxSegmentSize) {
        this.parameterArchive = parameterArchive;
        this.parameterIdMap = parameterArchive.getParameterIdDb();
        this.parameterGroupIdMap = parameterArchive.getParameterGroupIdDb();
        log = new Log(getClass(), parameterArchive.getYamcsInstance());
        this.maxSegmentSize = maxSegmentSize;
        log.debug("Archive filler task maxSegmentSize: {} ", maxSegmentSize);
    }

    void setCollectionSegmentStart(long collectionSegmentStart) {
        this.collectionSegmentStart = collectionSegmentStart;
    }

    /**
     * adds the parameters to the pgSegments structure
     * 
     * parameters older than collectionSegmentStart are ignored.
     * 
     * 
     * @param items
     * @return
     */
    void processParameters(List<ParameterValue> items) {
        Map<Long, BasicParameterList> m = new HashMap<>();
        for (ParameterValue pv : items) {
            long t = pv.getGenerationTime();
            if (t < collectionSegmentStart) {
                continue;
            }

            if (pv.getParameterQualifiedNamed() == null) {
                log.warn("No qualified name for parameter value {}, ignoring", pv);
                continue;
            }
            Value engValue = pv.getEngValue();
            if (engValue == null) {
                log.warn("Ignoring parameter without engineering value: {} ", pv.getParameterQualifiedNamed());
            }
            BasicParameterList l = m.computeIfAbsent(t, x -> new BasicParameterList(parameterIdMap));
            l.add(pv);
        }
        boolean needsFlush = false;
        long maxTimestamp = collectionSegmentStart;
        for (Map.Entry<Long, BasicParameterList> entry : m.entrySet()) {
            long t = entry.getKey();
            BasicParameterList pvList = entry.getValue();
            if (processParameters(t, pvList)) {
                needsFlush = true;
            }
            if (t > maxTimestamp) {
                maxTimestamp = t;
            }
        }
        if (needsFlush) {
            writeToArchive(collectionSegmentStart);
            collectionSegmentStart = maxTimestamp + 1;
        }
    }

    private boolean processParameters(long t, BasicParameterList pvList) {
        pvList.sort();
        numParams += pvList.size();
        try {
            int parameterGroupId = parameterGroupIdMap.createAndGet(pvList.getPids());

            PGSegment pgs = pgSegments.get(parameterGroupId);
            if (pgs == null) {
                pgs = new PGSegment(parameterGroupId, collectionSegmentStart, pvList.getPids());
                pgSegments.put(parameterGroupId, pgs);
            }

            pgs.addRecord(t, pvList.getValues());
            return pgs.size() > maxSegmentSize;

        } catch (RocksDBException e) {
            log.error("Error processing parameters", e);
            return false;
        }
    }

    void flush() {
        if (pgSegments != null) {
            writeToArchive(collectionSegmentStart);
        }
    }

    /**
     * writes data into the archive
     * 
     * @param pgList
     */
    protected void writeToArchive(long segStart) {
        log.debug("writing to archive semgent starting at {} with {} groups", TimeEncoding.toString(segStart),
                pgSegments.size());

        try {
            parameterArchive.writeToArchive(segStart, pgSegments.values());
        } catch (RocksDBException | IOException e) {
            log.error("failed to write data to the archive", e);
        }
        pgSegments.clear();
    }

    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        if (oomImminent()) {
            return;
        }

        long t = items.get(0).getGenerationTime();
        long t1 = ParameterArchive.getIntervalStart(t);

        if (t1 > collectionSegmentStart) {
            if (!pgSegments.isEmpty()) {
                writeToArchive(collectionSegmentStart);
            }
            collectionSegmentStart = t1;
        }

        processParameters(items);
    }

    public long getNumProcessedParameters() {
        return numParams;
    }

    private boolean oomImminent() {
        if (memoryBean != null && memoryBean.isCollectionUsageThresholdExceeded()) {
            aborted = true;

            String msg = "Aborting parameter archive filling due to imminent out of memory. Consider decreasing the maxSegmentSize (current value is "
                    + maxSegmentSize + ").";
            log.error(msg);
            pgSegments = null;
            processor.stopAsync();
            System.gc();
            return true;
        }
        return false;
    }

    static MemoryPoolMXBean getMemoryBean() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported()
                    && pool.getName().toLowerCase().contains("old")) {
                long threshold = (long) Math.floor(pool.getUsage().getMax() * 0.90);
                pool.setCollectionUsageThreshold(threshold);
                return pool;
            }
        }
        return null;
    }

    public void setProcessor(Processor proc) {
        this.processor = proc;
    }

    /**
     * If the archive filling has been aborted (due to imminent OOM) this returns true
     */
    boolean isAborted() {
        return aborted;
    }
}
