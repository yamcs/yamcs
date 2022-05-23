package org.yamcs.yarch.streamsql;

import java.math.BigDecimal;
import java.util.Set;

import org.yamcs.logging.Log;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.FilterableTarget;
import org.yamcs.yarch.HistogramReaderStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWalker;
import org.yamcs.yarch.TableReaderStream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

/**
 * A source of tuples. Can be:
 * 
 * <ul>
 * <li>a reference to an existing stream objectName
 * <li>a reference to a table objectName
 * <li>a stream expression
 * </ul>
 * 
 * @author nm
 *
 */
public class TupleSourceExpression implements FilterableTarget {
    static Log log = new Log(TupleSourceExpression.class);

    String objectName = null;
    StreamExpression streamExpression = null;
    BigDecimal histogramMergeTime = null;

    enum Type {
        STREAM_EXPRESSION, STREAM, TABLE, TABLE_HISTOGRAM;
    }

    // when histoColumn is set, the objectName must be a table having histograms on that column
    String histoColumn;

    boolean ascending = true;
    boolean follow = false;

    // after binding
    TupleDefinition definition;
    TableWalkerBuilder tableWalkerBuilder;
    HistogramStreamBuilder histogramStreamBuilder;

    Type type;

    public TupleSourceExpression(String name) {
        this.objectName = name;
    }

    public TupleSourceExpression(StreamExpression expr) {
        this.streamExpression = expr;
    }

    public void setHistogramColumn(String histoColumn) {
        this.histoColumn = histoColumn;
    }

    void bind(ExecutionContext c) throws StreamSqlException {
        if (streamExpression != null) {
            streamExpression.bind(c);
            definition = streamExpression.getOutputDefinition();
            type = Type.STREAM_EXPRESSION;
        } else {
            YarchDatabaseInstance ydb = c.getDb();
            TableDefinition tableDefinition = ydb.getTable(objectName);
            if (tableDefinition != null) {
                if (histoColumn == null) {
                    definition = tableDefinition.getTupleDefinition();
                    type = Type.TABLE;
                    tableWalkerBuilder = new TableWalkerBuilder(c, tableDefinition);
                    tableWalkerBuilder.setAscending(ascending);
                    tableWalkerBuilder.setFollow(follow);
                } else {
                    if (!tableDefinition.hasHistogram()) {
                        throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN,
                                "No histogram configured for table " + tableDefinition.getName());
                    }
                    if (!tableDefinition.getHistogramColumns().contains(histoColumn)) {
                        throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN,
                                "Histogram is not configured for column " + histoColumn);
                    }

                    histogramStreamBuilder = new HistogramStreamBuilder(ydb, tableDefinition, histoColumn);
                    definition = histogramStreamBuilder.getTupleDefinition();
                    if (histogramMergeTime != null) {
                        histogramStreamBuilder.setMergeTime(histogramMergeTime.longValue());
                    }
                    type = Type.TABLE_HISTOGRAM;
                }
            } else {
                Stream stream = ydb.getStream(objectName);
                if (stream == null) {
                    throw new ResourceNotFoundException(objectName);
                }
                if (histoColumn != null) {
                    throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN,
                            "Cannot specify histogram option when selecting from a stream");
                }
                definition = stream.getDefinition();
                type = Type.STREAM;
            }
        }
    }

    public void addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException {
        switch (type) {
        case STREAM:
        case STREAM_EXPRESSION:
            break;
        case TABLE:
            tableWalkerBuilder.addRelOpFilter(cexpr, relOp, value);
            break;
        case TABLE_HISTOGRAM:
            histogramStreamBuilder.addRelOpFilterHistogram(cexpr, relOp, value);
            break;
        default:
            throw new IllegalStateException();
        }
    }

    public void addInFilter(ColumnExpression cexpr, boolean negation, Set<Object> values) throws StreamSqlException {
        switch (type) {
        case STREAM:
        case STREAM_EXPRESSION:
            break;
        case TABLE:
            tableWalkerBuilder.addInFilter(cexpr, negation, values);
            break;
        case TABLE_HISTOGRAM:
            histogramStreamBuilder.addInFilter(cexpr, negation, values);
            break;
        default:
            throw new IllegalStateException();
        }
    }

    Stream execute(ExecutionContext c) throws StreamSqlException, YarchException {
        Stream stream;
        YarchDatabaseInstance ydb = c.getDb();

        switch (type) {
        case STREAM_EXPRESSION:
            stream = streamExpression.execute(c);
            break;
        case STREAM:
            stream = ydb.getStream(objectName);
            if (stream == null) {
                throw new ResourceNotFoundException(objectName);
            }
            break;
        case TABLE:
            TableWalker tblit = tableWalkerBuilder.build();
            stream = new TableReaderStream(ydb, tableWalkerBuilder.getTableDefinition(), tblit);
            break;
        case TABLE_HISTOGRAM:
            HistogramReaderStream histoStream = histogramStreamBuilder.build();

            stream = histoStream;
            break;
        default:
            throw new IllegalStateException();
        }

        return stream;
    }

    public void setHistogramMergeTime(BigDecimal mergeTime) {
        histogramMergeTime = mergeTime;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    public void setFollow(boolean follow) {
        this.follow = follow;
    }

    TupleDefinition getDefinition() {
        return definition;
    }

    public boolean isFinite() {
        switch(type) {
        case TABLE:
        case TABLE_HISTOGRAM:
            return true;
        default:
            return false;
        }
    }
}
