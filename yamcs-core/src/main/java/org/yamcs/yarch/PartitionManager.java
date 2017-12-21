package org.yamcs.yarch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.yamcs.utils.PartitionedTimeInterval;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.PartitioningSpec._type;

/**
 * Keeps track of partitions and histograms for one table.
 * 
 * The partitioning is organised in a set of time based partitioning intervals
 * (one day/month/year long)
 * 
 * Each interval has associated a set of value based partitions. In addition
 * each interval has a set of histograms - one for each table column for which
 * histograms have been created.
 * 
 * In the RocksDB implementation (both old and new) each interval corresponds to
 * one rocksdb database directory.
 * 
 * In case there is no time based partitioning, there is only one interval.
 * 
 * In case there is no value based partitioning, there is only one partition in
 * each interval.
 * 
 * @author nm
 *
 */
public abstract class PartitionManager {
    final protected TableDefinition tableDefinition;
    final protected PartitioningSpec partitioningSpec;

    protected PartitionedTimeInterval<Interval> intervals = new PartitionedTimeInterval<>();
    // pcache is a cache of the last interval where data has been inserted
    // in case of value based partition, it is basically the list of all
    // partitions
    protected Interval pcache;

    public PartitionManager(TableDefinition tableDefinition) {
        this.tableDefinition = tableDefinition;
        this.partitioningSpec = tableDefinition.getPartitioningSpec();
        if (partitioningSpec.type == _type.NONE || partitioningSpec.type == _type.VALUE) {
            // pcache never changes in this case
            pcache = new Interval();
            intervals.insert(pcache);
        }
    }

    /**
     * Returns an iterator which at each step gives the list of partition
     * corresponding to a time interval (so when we do a replay those partitions
     * have to be played in parallel). The iterator returns intervals sorted on
     * time.
     * 
     * 
     * @param partitionValueFilter
     *            - return only partitions whose value are in the filter. If
     *            null, return all partitions;
     * @return iterator going over partitions
     */
    public Iterator<List<Partition>> iterator(Set<Object> partitionValueFilter) {
        return  new PartitionIterator(partitioningSpec, intervals.iterator(), partitionValueFilter, false);
    }

    /**
     * same as above, only in reverse direction
     * 
     * @param partitionValueFilter
     * @return
     */
    public Iterator<List<Partition>> reverseIterator(Set<Object> partitionValueFilter) {
        return new PartitionIterator(partitioningSpec, intervals.reverseIterator(), partitionValueFilter, true);
    }

    /**
     * See {@link #iterator(Set)}
     * 
     * @param start
     * @param partitionValueFilter
     *            values - return only partitions whose value are in the filter.
     *            If null, return all partitions;
     * 
     * @return an iterator over the partitions starting at the specified start
     *         time
     * 
     */
    public Iterator<List<Partition>> iterator(long start, Set<Object> partitionValueFilter) {
        PartitionIterator pi = new PartitionIterator(partitioningSpec, intervals.iterator(), partitionValueFilter, false);
        pi.jumpToStart(start);
        return pi;
    }


    /**
     * Iterates over all intervals overlapping with the timeInterval.
     * 
     * Note that the timeInterval is considered closed at both ends (if set):
     * [timeInterval.start, timeInterval.stop] whereas the partition intervals
     * are considered closed at start and open at stop: [Interval.start,
     * Interval.stop)
     * 
     */
    public Iterator<Interval> intervalIterator(TimeInterval timeInterval) {
        return intervals.overlappingIterator(timeInterval);
    }

    public Iterator<List<Partition>> reverseIterator(long start, Set<Object> partitionValueFilter) {
        PartitionIterator pi = new PartitionIterator(partitioningSpec, intervals.reverseIterator(), partitionValueFilter, true);
        pi.jumpToStart(start);
        return pi;
    }

    /**
     * Creates (if not already existing) and returns the partition in which the
     * instant,value should be written.
     *
     * value can be null (in case of no value partitioning)
     * 
     * @param instant
     *            - time for which the partition has to be created - can be
     *            TimeEncoding.INVALID in case value only or no partitioning
     * @param value
     *            - value for which the partition has to be created - can be
     *            null in case of time only or no partitioning.
     * 
     *            For the enum partitions, the value is the index (type Short)
     *            rather than the string.
     * 
     * @return a Partition
     * @throws IOException
     */
    public synchronized Partition createAndGetPartition(long instant, Object value) throws IOException {
        Partition partition;
        Interval tmpInterval = pcache;

        if ((partitioningSpec.timeColumn != null) &&
                ((tmpInterval == null) || (!tmpInterval.contains0(instant)))) {
            tmpInterval = intervals.getFit(instant);
            if(tmpInterval==null) {
                TimePartitionInfo pinfo = partitioningSpec.getTimePartitioningSchema().getPartitionInfo(instant);
                tmpInterval = intervals.insert(new Interval(pinfo.getStart(), pinfo.getEnd()), 60000L);
                assert tmpInterval!=null;
            }
        }
        partition = tmpInterval.get(value);
        if (partition == null) {
            if (partitioningSpec.timeColumn != null) {
                TimePartitionInfo pinfo = partitioningSpec.getTimePartitioningSchema().getPartitionInfo(instant);
                partition = createPartitionByTime(pinfo, value);
            } else {
                partition = createPartition(value);
            }
            tmpInterval.add(value, partition);
        }
        pcache = tmpInterval;

        return partition;
    }

    public synchronized HistogramInfo createAndGetHistogram(long instant, String columnName) throws IOException {
        HistogramInfo histo;
        Interval tmpInterval = pcache;
        boolean newlyCreated = false;
        if ((partitioningSpec.timeColumn != null) &&
                ((tmpInterval == null) || (!tmpInterval.contains0(instant)))) {
            tmpInterval = intervals.getFit(instant);
            if(tmpInterval==null) {
                TimePartitionInfo pinfo = partitioningSpec.getTimePartitioningSchema().getPartitionInfo(instant);
                tmpInterval = intervals.insert(new Interval(pinfo.getStart(), pinfo.getEnd()), 60000L);
                assert tmpInterval!=null;
            }
        }

        histo = tmpInterval.getHistogram(columnName);
        if (histo == null) {
            if (partitioningSpec.timeColumn != null) {
                TimePartitionInfo pinfo = partitioningSpec.getTimePartitioningSchema().getPartitionInfo(instant);
                histo = createHistogramByTime(pinfo, columnName);
            } else {
                histo = createHistogram(columnName);
            }
            tmpInterval.addHistogram(columnName, histo);
        }
        pcache = tmpInterval;
        return histo;
    }

    /**
     * Gets partition where tuple has to be written. Creates the partition if
     * necessary.
     * 
     * @param t
     * 
     * @return the partition where the tuple has to be written
     * @throws IOException
     */
    public synchronized Partition getPartitionForTuple(Tuple t) throws IOException {
        long time = TimeEncoding.INVALID_INSTANT;
        Object value = null;
        if (partitioningSpec.timeColumn != null) {
            time = (Long) t.getColumn(partitioningSpec.timeColumn);
        }
        if (partitioningSpec.valueColumn != null) {
            value = t.getColumn(partitioningSpec.valueColumn);
            ColumnDefinition cd = tableDefinition.getColumnDefinition(partitioningSpec.valueColumn);
            if (cd.getType() == DataType.ENUM) {
                value = tableDefinition.addAndGetEnumValue(partitioningSpec.valueColumn, (String) value);
            }
        }
        return createAndGetPartition(time, value);
    }

    /**
     * Create a partition for time (and possible value) based partitioning
     * 
     * @param pinfo
     * @param value
     * @return
     * @throws IOException
     */
    protected abstract Partition createPartitionByTime(TimePartitionInfo pinfo, Object value) throws IOException;

    /**
     * Create a partition for value based partitioning
     * 
     * @param value
     * @return
     * @throws IOException
     */
    protected abstract Partition createPartition(Object value) throws IOException;

    protected abstract HistogramInfo createHistogramByTime(TimePartitionInfo pinfo, String columnName) throws IOException;

    protected abstract HistogramInfo createHistogram(String columnName) throws IOException;

    /**
     * Retrieves the existing partitions
     * 
     * @return list of all existing partitions
     */
    public List<Partition> getPartitions() {
        List<Partition> plist = new ArrayList<>();
        for (Interval interval : intervals) {
            plist.addAll(interval.partitions.values());
        }
        return plist;
    }

    /**
     * Keeps a value -&gt; partition map for a specific time interval
     *
     */
    public static class Interval extends TimeInterval {
        // we use this as a key in the ConcurrentHashMap in case value is null (i.e. time only partitioning)
        static final Object NON_NULL = new Object(); 

        Map<Object, Partition> partitions = new ConcurrentHashMap<>();

        // columnName -> Histogram for this interval
        Map<String, HistogramInfo> histograms = new ConcurrentHashMap<>();

        public Interval(long start, long stop) {
            super(start, stop);
        }

        /**
         * Constructs an interval without start or stop (covers all time)
         */
        public Interval() {
            super();
        }

        public Partition get(Object value) {
            if (value == null) {
                return partitions.get(NON_NULL);
            } else {
                return partitions.get(value);
            }
        }

        public void addTimePartition(Partition partition) {
            partitions.put(NON_NULL, partition);
        }

        /**
         * Add a partition
         * 
         * @param value
         *            - can be null in case of time based partitioning
         */
        public void add(Object value, Partition partition) {
            if (value != null) {
                partitions.put(value, partition);
            } else {
                addTimePartition(partition);
            }
        }

        public void addHistogram(String columnName, HistogramInfo histo) {
            histograms.put(columnName, histo);
        }

        public Map<Object, Partition> getPartitions() {
            return Collections.unmodifiableMap(partitions);
        }


        public HistogramInfo getHistogram(String columnName) {
            return histograms.get(columnName);
        }

        public Collection<HistogramInfo> getHistograms() {
            return histograms.values();
        }

        @Override
        public String toString() {
            return "[" + TimeEncoding.toString(getStart()) + "(" + getStart() + ") - " + TimeEncoding.toString(getEnd()) + "(" + getEnd()
            + ")] values: " + partitions;
        }

        public Collection<HistogramInfo> removeHistograms() {
            List<HistogramInfo> l = new ArrayList<>(histograms.values());
            for (String name : histograms.keySet()) {
                l.add(histograms.remove(name));
            }
            return l;
        }
    }
}
