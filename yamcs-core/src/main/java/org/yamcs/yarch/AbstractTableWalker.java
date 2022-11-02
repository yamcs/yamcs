package org.yamcs.yarch;

import java.util.Iterator;
import java.util.Set;

import org.yamcs.logging.Log;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Iterator through a table.
 * <p>
 * Iterates through partitions, can support partition filter (by time and/or value) and and also ranges on primary key.
 * <p>This class expects raw (byte[]) input for the primary key ranges.
 * 
 * @author nm
 *
 */
public abstract class AbstractTableWalker implements TableWalker {
    protected Log log;

    protected TableDefinition tableDefinition;

    // if not null, the iterate should run in this range
    //by default everything
    private DbRange range = new DbRange();

    //// if not null, only includes data from these partitions
    private Set<Object> partitionValueFilter;
    TimeInterval partitionTimeFilter;
    
    final protected boolean ascending;
    final protected boolean follow;
   
    protected long numRecordsRead = 0;
  
    volatile protected boolean running = false;

    protected final YarchDatabaseInstance ydb;
    protected final ExecutionContext ctx;

    protected AbstractTableWalker(ExecutionContext ctx, TableDefinition tableDefinition, boolean ascending,
            boolean follow) {
        this.tableDefinition = tableDefinition;
        this.ctx = ctx;
        this.ydb = ctx.getDb();

        this.ascending = ascending;
        this.follow = follow;
        log = new Log(getClass(), ydb.getName());
    }

    @Override
    public void walk(TableVisitor visitor) throws StreamSqlException {
        if (visitor == null) {
            throw new NullPointerException("visitor cannot be null");
        }
        log.debug("Starting to walk ascending: {}, rangeIndexFilter: {}", ascending, range, visitor);

        running = true;
        Iterator<PartitionManager.Interval> partitionIterator = getIntervalIterator();
        try {
            while (isRunning() && partitionIterator.hasNext()) {
                PartitionManager.Interval interval = partitionIterator.next();
                boolean endReached = walkInterval(interval, range, visitor);
                if (endReached) {
                    break;
                }
            }
        } finally {
            close();
        }
    }


    private Iterator<PartitionManager.Interval> getIntervalIterator() {
        PartitionManager partitionManager = ydb.getPartitionManager(tableDefinition);
        Iterator<PartitionManager.Interval> partitionIterator;

        PartitioningSpec pspec = tableDefinition.getPartitioningSpec();
        if (pspec.valueColumn != null) {
            if ((ascending) && (partitionTimeFilter != null) && partitionTimeFilter.hasStart()) {
                long start = partitionTimeFilter.getStart();
                partitionIterator = partitionManager.iterator(start, partitionValueFilter);
            } else if ((!ascending) && (partitionTimeFilter != null) && partitionTimeFilter.hasEnd()) {
                long start = partitionTimeFilter.getEnd();
                partitionIterator = partitionManager.reverseIterator(start, partitionValueFilter);
            } else {
                if (ascending) {
                    partitionIterator = partitionManager.iterator(partitionValueFilter);
                } else {
                    partitionIterator = partitionManager.reverseIterator(partitionValueFilter);
                }
            }
        } else {
            if (ascending) {
                partitionIterator = partitionManager.iterator(partitionValueFilter);
            } else {
                partitionIterator = partitionManager.reverseIterator(partitionValueFilter);
            }
        }
        return partitionIterator;
    }

    protected boolean iAscendingFinished(byte[] key, byte[] value, byte[] rangeEnd) {
        boolean finished = false;
        if (rangeEnd != null) { // check if we have reached the end
            int c = ByteArrayUtils.compare(key, rangeEnd);
            if (c <= 0) {
                finished = false;
            } else {
                finished = true;
            }
        }
        return finished;
    }

    protected boolean isDescendingFinished(byte[] key, byte[] value, byte[] rangeStart) {
        boolean finished = false;
        if (rangeStart != null) { // check if we have reached the start
            int c = ByteArrayUtils.compare(key, rangeStart);
            if (c >= 0) {
                finished = false;
            } else {
                finished = true;
            }
        }
        return finished;
    }

    @Override
    public void setPartitionFilter(TimeInterval partitionTimeFilter, Set<Object> partitionValueFilter) {
        this.partitionValueFilter = partitionValueFilter;
        this.partitionTimeFilter = partitionTimeFilter;
    }

    @Override
    public void setPrimaryIndexRange(DbRange range) {
        if(range == null) {
            throw new NullPointerException();
        }
        this.range = range;
    }
    
    /**
     * Runs the data in a time interval (corresponding to a time partition) sending data only that conform with the
     * start and end filters. Returns true if the stop condition is met
     * 
     * @return returns true if the end condition has been reached.
     * @throws StreamSqlException
     */
    protected abstract boolean walkInterval(PartitionManager.Interval interval, DbRange range, TableVisitor visitor)
            throws YarchException, StreamSqlException;

    protected boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        running = false;
    }
}
