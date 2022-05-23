package org.yamcs.yarch.streamsql;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.logging.Log;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.FilterableTarget;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TableColumnDefinition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWalker;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import com.google.common.collect.BiMap;

public class TableWalkerBuilder implements FilterableTarget {
    static Log log = new Log(TableWalkerBuilder.class);

    final private ExecutionContext ctx;
    final private YarchDatabaseInstance ydb;
    final private TableDefinition tableDefinition;

    private Set<Object> partitionValueFilter;
    TimeInterval partitionTimeFilter = new TimeInterval();

    // filter on primary key
    private DbRange pkRange;

    // filter on secondary key
    private DbRange skRange;

    private boolean ascending = true;
    private boolean follow = false;

    public TableWalkerBuilder(ExecutionContext ctx, TableDefinition tableDefinition) {
        this.ctx = ctx;
        this.ydb = ctx.getDb();
        this.tableDefinition = tableDefinition;
    }

    @Override
    public void addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException {
        String columnName = cexpr.getName();

        TableColumnDefinition col0 = tableDefinition.getKeyDefinition().get(0);
        if (col0.getName().equals(columnName)) {
            byte[] val = null;
            Object columnValue = null;
            try {
                columnValue = DataType.castAs(col0.getType(), value);
                val = col0.getSerializer().toByteArray(columnValue);
            } catch (IllegalArgumentException e) {
                throw new StreamSqlException(ErrCode.ERROR, e.getMessage());
            }
            if (pkRange == null) {
                pkRange = new DbRange();
            }
            addToRange(pkRange, relOp, val);

            if (tableDefinition.isPartitionedByTime()) {
                addPartitionTimeFilter(relOp, (Long) columnValue);
            }
        } else {
            List<String> sidx = tableDefinition.getSecondaryIndex();
            if (sidx != null && sidx.get(0).equals(columnName)) {
                TableColumnDefinition tcd = tableDefinition.getColumnDefinition(columnName);
                byte[] val = null;
                try {
                    Object columnValue = DataType.castAs(tcd.getType(), value);
                    val = tcd.getSerializer().toByteArray(columnValue);
                } catch (IllegalArgumentException e) {
                    throw new StreamSqlException(ErrCode.ERROR, e.getMessage());
                }
                if (skRange == null) {
                    skRange = new DbRange();
                }
                byte[] b = new byte[val.length+1];
                b[0] = (byte)(0x70|tcd.getType().getTypeId());
                System.arraycopy(val, 0, b, 1, val.length);
                addToRange(skRange, relOp, b);
            }
        }

        if ((relOp == RelOp.EQUAL) && tableDefinition.hasPartitioning()) {
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
            }
        }
    }

    private void addPartitionTimeFilter(RelOp relOp, long time) {
        switch (relOp) {
        case GREATER:
        case GREATER_OR_EQUAL:
            partitionTimeFilter.setStart(time);
            break;
        case LESS:
        case LESS_OR_EQUAL:
            partitionTimeFilter.setEnd(time);
            break;
        case EQUAL:
            partitionTimeFilter.setStart(time);
            partitionTimeFilter.setEnd(time);
            break;
        case NOT_EQUAL:
            // TODO support multiple ranges
            break;
        case OVERLAP:
            throw new IllegalStateException();
        }
    }

    private void addToRange(DbRange range, RelOp relOp, byte[] val) {

        // TODO FIX to allow multiple ranges
        switch (relOp) {
        case GREATER:
        case GREATER_OR_EQUAL:
            range.rangeStart = val;
            break;
        case LESS:
        case LESS_OR_EQUAL:
            range.rangeEnd = val;
            break;
        case EQUAL:
            range.rangeStart = val;
            range.rangeEnd = val;
            break;
        case NOT_EQUAL:
            // TODO - two ranges have to be created
            break;
        case OVERLAP:
            throw new IllegalStateException();
        }
    }

    /**
     * currently adds only filters on value based partitions
     */
    @Override
    public void addInFilter(ColumnExpression cexpr, boolean negation, Set<Object> values) throws StreamSqlException {
        if (!tableDefinition.hasPartitioning()) {
            return;
        }
        PartitioningSpec pspec = tableDefinition.getPartitioningSpec();

        if ((pspec.valueColumn == null) || (!pspec.valueColumn.equals(cexpr.getName()))) {
            return;
        }
        values = transformEnums(values);
        if (partitionValueFilter == null) {
            if (negation) {
                ColumnDefinition cd = tableDefinition.getColumnDefinition(pspec.valueColumn);
                if (cd.getType() != DataType.ENUM) { // we don't know all the possible values so we cannot exclude
                    return;
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
        return;
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

    public TableWalker build() {
        if (!ascending) {
            follow = false;
        }
        TableWalker tw;
        if (skRange == null) {
            tw = ydb.getStorageEngine(tableDefinition).newTableWalker(ctx, tableDefinition, ascending, follow);
            tw.setPartitionFilter(partitionTimeFilter, partitionValueFilter);
        } else {
            tw = ydb.getStorageEngine(tableDefinition).newSecondaryIndexTableWalker(ydb, tableDefinition, ascending,
                    follow);
            tw.setSecondaryIndexRange(skRange);
        }
      
        if (pkRange != null) {
            tw.setPrimaryIndexRange(pkRange);
        }
        return tw;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    public void setFollow(boolean follow) {
        this.follow = follow;
    }

    public TableDefinition getTableDefinition() {
        return tableDefinition;
    }

}
