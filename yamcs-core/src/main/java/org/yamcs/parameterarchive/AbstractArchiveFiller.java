package org.yamcs.parameterarchive;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;

/**
 * Archive filler that creates segments of max size .
 * 
 * @author nm
 *
 */
abstract class AbstractArchiveFiller implements ParameterConsumer {
    final ParameterArchive parameterArchive;
    final protected Log log;

    long numParams = 0;
    static int DEFAULT_MAX_SEGMENT_SIZE = 5000;
    static MemoryPoolMXBean memoryBean = getMemoryBean();
    protected final ParameterIdDb parameterIdMap;
    protected final ParameterGroupIdDb parameterGroupIdMap;

    // ignore any data older than this
    // when doing backfilling, there is a warming up interval - the replay is started with older data
    protected long collectionStart;

    protected int maxSegmentSize;
    boolean aborted = false;

    public AbstractArchiveFiller(ParameterArchive parameterArchive) {
        this.parameterArchive = parameterArchive;
        this.parameterIdMap = parameterArchive.getParameterIdDb();
        this.parameterGroupIdMap = parameterArchive.getParameterGroupIdDb();
        log = new Log(getClass(), parameterArchive.getYamcsInstance());
        this.maxSegmentSize = parameterArchive.getMaxSegmentSize();
        log.debug("Archive filler task maxSegmentSize: {} ", maxSegmentSize);
    }

    void setCollectionStart(long collectionStart) {
        this.collectionStart = collectionStart;
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
            if (t < collectionStart) {
                continue;
            }

            if (pv.getParameterQualifiedName() == null) {
                log.warn("No qualified name for parameter value {}, ignoring", pv);
                continue;
            }
            Value engValue = pv.getEngValue();
            if (engValue == null) {
                log.warn("Ignoring parameter without engineering value: {} ", pv.getParameterQualifiedName());
                continue;
            }
            BasicParameterList l = m.computeIfAbsent(t, x -> new BasicParameterList(parameterIdMap));
            l.add(pv);
        }
        for (Map.Entry<Long, BasicParameterList> entry : m.entrySet()) {
            long t = entry.getKey();
            BasicParameterList pvList = entry.getValue();
            pvList.sort();
            processParameters(t, pvList);
            numParams += pvList.size();
        }
    }

    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        if (oomImminent()) {
            return;
        }

        processParameters(items);
    }

    public long getNumProcessedParameters() {
        return numParams;
    }

    /**
     * If the archive filling has been aborted (due to imminent OOM) this returns true
     */
    boolean isAborted() {
        return aborted;
    }

    protected abstract void processParameters(long t, BasicParameterList pvList);

    protected abstract void abort();

    private boolean oomImminent() {
        if (memoryBean != null && memoryBean.isCollectionUsageThresholdExceeded()) {
            aborted = true;
            String msg = "Aborting parameter archive filling due to imminent out of memory. Consider decreasing the maxSegmentSize (current value is "
                    + maxSegmentSize + ").";
            log.error(msg);
            abort();
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
}
