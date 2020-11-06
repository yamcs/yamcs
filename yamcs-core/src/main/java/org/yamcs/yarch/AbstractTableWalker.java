package org.yamcs.yarch;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.yamcs.logging.Log;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.streamsql.ColumnExpression;
import org.yamcs.yarch.streamsql.RelOp;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import com.google.common.collect.BiMap;

/**
 * Iterator through a table.
 * <p>
 * Takes care of iterating through partitions, and also utilises the indices
 * 
 * @author nm
 *
 */
public abstract class AbstractTableWalker implements TableWalker {
    protected TableDefinition tableDefinition;

    // if not null, the iterate should run in this range
    private IndexFilter rangeIndexFilter;

    //// if not null, only includes data from these partitions
    // - if the table is partitioned on a non index column
    private Set<Object> partitionValueFilter;
    final protected PartitionManager partitionManager;
    final protected boolean ascending;
    final protected boolean follow;

    protected Log log;

    volatile boolean running = false;

    protected TableVisitor visitor;

    protected AbstractTableWalker(YarchDatabaseInstance ydb, PartitionManager partitionManager, boolean ascending,
            boolean follow) {
        this.tableDefinition = partitionManager.getTableDefinition();
        this.partitionManager = partitionManager;
        this.ascending = ascending;
        this.follow = follow;
        log = new Log(getClass(), ydb.getName());
    }

    @Override
    public void walk(TableVisitor visitor) {
        if (visitor == null) {
            throw new NullPointerException("visitor cannot be null");
        }
        log.debug("Starting to walk ascending: {}, rangeIndexFilter: {}", ascending, rangeIndexFilter);
        this.visitor = visitor;
        running = true;
        Iterator<List<Partition>> partitionIterator = getPartitionIterator();
        try {
            while (isRunning() && partitionIterator.hasNext()) {
                List<Partition> partitions = partitionIterator.next();
                boolean endReached = walkPartitions(partitions, rangeIndexFilter);
                if (endReached) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("got exception ", e);
        } finally {
            close();
        }
    }

    @Override
    public void bulkDelete() {
        running = true;
        Iterator<List<Partition>> partitionIterator = getPartitionIterator();
        try {
            while (isRunning() && partitionIterator.hasNext()) {
                List<Partition> partitions = partitionIterator.next();
                boolean endReached = bulkDeleteFromPartitions(partitions, rangeIndexFilter);
                if (endReached) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("got exception ", e);
        } finally {
            close();
        }
    }

    private Iterator<List<Partition>> getPartitionIterator() {
        Iterator<List<Partition>> partitionIterator;

        PartitioningSpec pspec = tableDefinition.getPartitioningSpec();
        if (pspec.valueColumn != null) {
            if ((ascending) && (rangeIndexFilter != null) && (rangeIndexFilter.keyStart != null)) {
                long start = (Long) rangeIndexFilter.keyStart;
                partitionIterator = partitionManager.iterator(start, partitionValueFilter);
            } else if ((!ascending) && (rangeIndexFilter != null) && (rangeIndexFilter.keyEnd != null)) {
                long start = (Long) rangeIndexFilter.keyEnd;
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

    protected boolean iAscendingFinished(byte[] key, byte[] value, byte[] rangeEnd, boolean strictEnd) {
        boolean finished = false;
        if (rangeEnd != null) { // check if we have reached the end
            int c = ByteArrayUtils.compare(key, rangeEnd);
            if (c < 0 || (c == 0 && !strictEnd)) {
                finished = false;
            } else {
                finished = true;
            }
        }
        return finished;
    }

    protected boolean isDescendingFinished(byte[] key, byte[] value, byte[] rangeStart, boolean strictStart) {
        boolean finished = false;
        if (rangeStart != null) { // check if we have reached the start
            int c = ByteArrayUtils.compare(key, rangeStart);
            if (c > 0) {
                finished = false;
            } else if ((c == 0) && (!strictStart)) {
                finished = false;
            } else {
                finished = true;
            }
        }
        return finished;
    }

    /**
     * Runs the partitions sending data only that conform with the start and end filters. returns true if the stop
     * condition is met
     * 
     * All the partitions are from the same time interval
     * 
     * @return returns true if the end condition has been reached.
     */
    protected abstract boolean walkPartitions(List<Partition> partitions, IndexFilter range) throws YarchException;

    protected abstract boolean bulkDeleteFromPartitions(List<Partition> partitions, IndexFilter range)
            throws YarchException;

    @Override
    public boolean addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException {
        if (tableDefinition.isIndexedByKey(cexpr.getName())) {
            ColumnDefinition cdef = tableDefinition.getColumnDefinition(cexpr.getName());
            Comparable<Object> cv = null;
            try {
                cv = (Comparable<Object>) DataType.castAs(cdef.getType(), value);
            } catch (IllegalArgumentException e) {
                throw new StreamSqlException(ErrCode.ERROR, e.getMessage());
            }
            if (rangeIndexFilter == null) {
                rangeIndexFilter = new IndexFilter();
            }
            // TODO FIX to allow multiple ranges
            switch (relOp) {
            case GREATER:
                rangeIndexFilter.keyStart = cv;
                rangeIndexFilter.strictStart = true;
                break;
            case GREATER_OR_EQUAL:
                rangeIndexFilter.keyStart = cv;
                rangeIndexFilter.strictStart = false;
                break;
            case LESS:
                rangeIndexFilter.keyEnd = cv;
                rangeIndexFilter.strictEnd = true;
                break;
            case LESS_OR_EQUAL:
                rangeIndexFilter.keyEnd = cv;
                rangeIndexFilter.strictEnd = false;
                break;
            case EQUAL:
                rangeIndexFilter.keyStart = rangeIndexFilter.keyEnd = cv;
                rangeIndexFilter.strictStart = rangeIndexFilter.strictEnd = false;
                break;
            case NOT_EQUAL:
                // TODO - two ranges have to be created
            }
            return true;
        } else if ((relOp == RelOp.EQUAL) && tableDefinition.hasPartitioning()) {
            PartitioningSpec pspec = tableDefinition.getPartitioningSpec();
            if (cexpr.getName().equals(pspec.valueColumn)) {
                Set<Object> values = new HashSet<>();
                values.add(value);
                values = transformEnums(values);
                if (partitionValueFilter == null) {
                    partitionValueFilter = values;
                } else {
                    partitionValueFilter.retainAll(values);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * currently adds only filters on value based partitions
     */
    @Override
    public boolean addInFilter(ColumnExpression cexpr, boolean negation, Set<Object> values) throws StreamSqlException {
        if (!tableDefinition.hasPartitioning()) {
            return false;
        }
        PartitioningSpec pspec = tableDefinition.getPartitioningSpec();

        if ((pspec.valueColumn == null) || (!pspec.valueColumn.equals(cexpr.getName()))) {
            return false;
        }

        values = transformEnums(values);
        if (partitionValueFilter == null) {
            if (negation) {
                ColumnDefinition cd = tableDefinition.getColumnDefinition(pspec.valueColumn);
                if (cd.getType() != DataType.ENUM) { // we don't know all the possible values so we cannot exclude
                    return false;
                }
                BiMap<String, Short> enumValues = tableDefinition.getEnumValues(pspec.valueColumn);
                partitionValueFilter = new HashSet<>(enumValues.values());
                partitionValueFilter.removeAll(values);
            } else {
                partitionValueFilter = values;
            }
        } else {
            if (negation) {
                partitionValueFilter.removeAll(values);
            } else {
                partitionValueFilter.retainAll(values);
            }
        }
        return true;
    }

    // if the value partitioning column is of type Enum, we have to convert all
    // the values (used in the query for filtering) from String to Short 
    // the values that do not have an enum are eliminated (because they cannot be possibly matching the query)

    // if partitioning value is not an enum, return it unchanged
    private Set<Object> transformEnums(Set<Object> values) {
        PartitioningSpec pspec = tableDefinition.getPartitioningSpec();
        ColumnDefinition cd = tableDefinition.getColumnDefinition(pspec.valueColumn);

        if (cd.getType() == DataType.ENUM) {
            BiMap<String, Short> enumValues = tableDefinition.getEnumValues(pspec.valueColumn);

            Set<Object> v1 = new HashSet<>();
            if (enumValues != null) { // else there is no value in the table yet
                for (Object o : values) {
                    Object o1 = enumValues.get(o);
                    if (o1 == null) {
                        log.debug("no enum value for column: {} value: {}", pspec.valueColumn, o);
                    } else {
                        v1.add(o1);
                    }
                }
            }
            values = v1;
        }
        return values;
    }

    protected boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        running = false;
    }
}
