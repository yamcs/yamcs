package org.yamcs.yarch.streamsql;

import java.util.Set;

import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.HistogramReaderStream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class HistogramStreamBuilder {
    private final YarchDatabaseInstance ydb;
    private final TableDefinition tableDefinition;
    private final String columnName;
    private final TupleDefinition tupleDefinition;

    private long mergeTime = -1;
    private TimeInterval interval = new TimeInterval();

    HistogramStreamBuilder(YarchDatabaseInstance ydb, TableDefinition tableDefinition, String columnName) {
        this.ydb = ydb;
        this.tableDefinition = tableDefinition;
        this.columnName = columnName;

        tupleDefinition = new TupleDefinition();
        tupleDefinition.addColumn(tableDefinition.getColumnDefinition(columnName));
        tupleDefinition.addColumn(new ColumnDefinition("first", DataType.TIMESTAMP));
        tupleDefinition.addColumn(new ColumnDefinition("last", DataType.TIMESTAMP));
        tupleDefinition.addColumn(new ColumnDefinition("num", DataType.INT));
    }

    /* puts conditions on the first or last. doesn't work properly yet TODO */
    public boolean addRelOpFilterHistogram(ColumnExpression cexpr, RelOp relOp, Object value)
            throws StreamSqlException {
        String cname = cexpr.getName();
        if ("first".equals(cname) || "last".equals(cname)) {
            long time;
            try {
                time = (Long) DataType.castAs(DataType.TIMESTAMP, value);
            } catch (IllegalArgumentException e) {
                throw new StreamSqlException(ErrCode.ERROR, e.getMessage());
            }
            switch (relOp) {
            case GREATER:
            case GREATER_OR_EQUAL:
                interval.setStart(time);
                return true;
            case LESS:
            case LESS_OR_EQUAL:
                interval.setEnd(time);
                return true;
            case EQUAL:
                interval.setStart(time);
                interval.setEnd(time);
                return true;
            default:
                // TODO
                throw new UnsupportedOperationException(relOp + " not implemented for histogram streams");
            }
        }
        return false;
    }

    public boolean addInFilter(ColumnExpression cexpr, boolean negation, Set<Object> values) {
        return false;
    }

    public HistogramReaderStream build() {
        HistogramReaderStream histoStream = new HistogramReaderStream(ydb, tableDefinition, columnName,
                tupleDefinition);
        if (mergeTime > 0) {
            histoStream.setMergeTime(mergeTime);
        }
        histoStream.setTimeInterval(interval);

        return histoStream;
    }

    public TupleDefinition getTupleDefinition() {
        return tupleDefinition;
    }

    public void setMergeTime(long mergeTime) {
        this.mergeTime = mergeTime;
    }
}
