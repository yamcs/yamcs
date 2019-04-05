package org.yamcs.parameterarchive;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.LoggingUtils;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.yamcs.YConfiguration;
import org.yamcs.Processor;

/**
 * Realtime archive filler task - it works even if the data is not perfectly sorted
 * 
 * We can save data in max two intervals at a time. The first interval we keep open only as long as
 * the most recent timestamp received is not older than orderingThreshold ms from the interval end
 * 
 * 
 * When we receive a new delivery, we sort the parameters into groups, all parameter from the same group having the same
 * timestamp
 * One group corresponds to a set of parameter,types and is written in a segment.
 * 
 * We keep open max two segments for each group, one in each interval.
 * 
 * If the group reaches its max size, we archive it and open another one.
 * 
 * @author nm
 *
 */
public class RealtimeArchiveFiller extends AbstractExecutionThreadService implements ParameterConsumer {
    int flushInterval; // seconds
    String processorName = "realtime";
    final String yamcsInstance;
    Processor realtimeProcessor;
    int subscriptionId;
    protected final ParameterIdDb parameterIdMap;
    protected final ParameterGroupIdDb parameterGroupIdMap;
    final ParameterArchive parameterArchive;
    final private Logger log;
    final BlockingQueue<List<ParameterValue>> queue = new ArrayBlockingQueue<>(10);

    // max allowed time for old data
    long threshold;
    int maxSegmentSize;

    long numParams = 0;
    ArchiveIntervalFiller first, second;

    public RealtimeArchiveFiller(ParameterArchive parameterArchive, YConfiguration config) {
        this.parameterArchive = parameterArchive;
        this.parameterIdMap = parameterArchive.getParameterIdDb();
        this.parameterGroupIdMap = parameterArchive.getParameterGroupIdDb();
        this.yamcsInstance = parameterArchive.getYamcsInstance();
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);

        if (config != null) {
            parseConfig(config);
        }
    }

    private void parseConfig(YConfiguration config) {
        flushInterval = config.getInt("flushInterval", 300);
        processorName = config.getString("processorName", processorName);
        maxSegmentSize = config.getInt("maxSegmentSize", ArchiveFillerTask.DEFAULT_MAX_SEGMENT_SIZE);
        threshold = config.getInt("orderingThreshold", 20000);
    }

    @Override
    protected void run() throws Exception {
        while (isRunning()) {
            List<ParameterValue> items = queue.poll(flushInterval, TimeUnit.SECONDS);
            if ((items == null) || items.isEmpty()) {
                flush();
                continue;
            }
            if (first == null) { // this is the first delivery
                long t = items.stream().mapToLong(pv -> pv.getGenerationTime()).min().getAsLong();
                first = new ArchiveIntervalFiller(parameterArchive, log, 
                        ParameterArchive.getIntervalStart(t), maxSegmentSize);
            }
            long t = processParameters(items);
            if (t < 0) {
                continue;
            }

            if ((second != null) && (t > second.intervalStart + threshold)) {
                first.flush();
                first = second;
                second = null;
            }
        }
        flush();
    }

    // send the parameters to the processing thread
    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        try {
            queue.put(items);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void flush() {
        try {
            if (first != null) {
                first.flush();
            }
            if (second != null) {
                second.flush();
            }
        } catch (Exception e) {
            log.error("Exception flushing the parameter archive");
        }
    }

    @Override
    protected void startUp() {
        // subscribe to the realtime processor
        realtimeProcessor = Processor.getInstance(yamcsInstance, processorName);
        if (realtimeProcessor == null) {
            throw new ConfigurationException("No processor named '" + processorName + "' in instance " + yamcsInstance);
        }
        subscriptionId = realtimeProcessor.getParameterRequestManager().subscribeAll(this);
    }

    @Override
    protected void shutDown() {
        realtimeProcessor.getParameterRequestManager().unsubscribeAll(subscriptionId);
    }

    /**
     * adds the parameters to the pgSegments structure and return the highest timestamp or -1 if all parameters have
     * been ignored (because they were too old)
     * 
     * parameters older than ignoreOlderThan are ignored.
     * 
     * 
     * @param items
     * @return
     * @throws RocksDBException
     * @throws IOException
     */
    protected long processParameters(List<ParameterValue> items) throws IOException, RocksDBException {
        Map<Long, BasicParameterList> m = new HashMap<>();
        for (ParameterValue pv : items) {
            long t = pv.getGenerationTime();
            if (t < first.intervalStart) {
                continue;
            }
            if (pv.getParameterQualifiedNamed() == null) {
                log.warn("No qualified name for parameter value {}, ignoring", pv);
                continue;
            }
            BasicParameterList l = m.computeIfAbsent(t, k -> new BasicParameterList(parameterIdMap));
            l.add(pv);
        }
        long maxTimestamp = -1;
        for (Map.Entry<Long, BasicParameterList> entry : m.entrySet()) {
            long t = entry.getKey();
            BasicParameterList pvList = entry.getValue();
            long is = ParameterArchive.getIntervalStart(t);
            if(is == first.intervalStart) {
                first.addParameters(t, pvList);    
            } else {
                if(second==null) {
                    second = new ArchiveIntervalFiller(parameterArchive, log, is, maxSegmentSize);
                }
                second.addParameters(t, pvList);
            }
            
            if (t > maxTimestamp) {
                maxTimestamp = t;
            }
        }
        return maxTimestamp;
    }

    /**
     * writes data into the archive
     * 
     * @param pgList
     */
    protected void writeToArchive(long segStart, Collection<PGSegment> pgList) {
        try {
            parameterArchive.writeToArchive(segStart, pgList);
        } catch (RocksDBException | IOException e) {
            log.error("failed to write data to the archive", e);
        }
    }

    public long getNumProcessedParameters() {
        return numParams;
    }
}
