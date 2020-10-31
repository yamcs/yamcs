package org.yamcs.yarch.streamsql;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
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
public class TupleSourceExpression {
    String objectName = null;
    StreamExpression streamExpression = null;
    BigDecimal histogramMergeTime = null;

    // when histoColumn is set, the objectName must be a table having histograms on that column
    String histoColumn;

    boolean ascending = true;
    boolean follow = false;

    // after binding
    TupleDefinition definition;

    static Logger log = LoggerFactory.getLogger(TupleSourceExpression.class.getName());

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
        } else {
            YarchDatabaseInstance ydb = c.getDb();
            TableDefinition tbl = ydb.getTable(objectName);
            if (tbl != null) {
                if (histoColumn == null) {
                    definition = tbl.getTupleDefinition();
                } else {
                    if (!tbl.hasHistogram()) {
                        throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN,
                                "No histogram configured for table " + tbl.getName());
                    }
                    if (!tbl.getHistogramColumns().contains(histoColumn)) {
                        throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN,
                                "Histogram is not configured for column " + histoColumn);
                    }

                    definition = new TupleDefinition();
                    definition.addColumn(tbl.getColumnDefinition(histoColumn));
                    definition.addColumn(new ColumnDefinition("first", DataType.TIMESTAMP));
                    definition.addColumn(new ColumnDefinition("last", DataType.TIMESTAMP));
                    definition.addColumn(new ColumnDefinition("num", DataType.INT));
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
            }
        }
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

    Stream execute(ExecutionContext c) throws StreamSqlException {
        Stream stream;
        if (streamExpression != null) {
            stream = streamExpression.execute(c);
        } else if (objectName != null) {
            YarchDatabaseInstance ydb = c.getDb();
            if (!ascending) {
                follow = false;
            }
            TableDefinition tbl = ydb.getTable(objectName);
            if (tbl != null) {
                if (histoColumn == null) {
                    TableWalker tblit = ydb.getStorageEngine(tbl).newTableWalker(ydb, tbl, ascending, follow);
                    stream = new TableReaderStream(ydb, tbl, tblit);
                } else {
                    HistogramReaderStream histoStream;
                    try {
                        histoStream = new HistogramReaderStream(ydb, tbl, histoColumn, definition);
                    } catch (YarchException e) {
                        throw new StreamSqlException(ErrCode.ERROR, e.getMessage());
                    }
                    if (histogramMergeTime != null) {
                        histoStream.setMergeTime(histogramMergeTime.longValue());
                    }
                    stream = histoStream;
                }
            } else {
                stream = ydb.getStream(objectName);
                if (stream == null) {
                    throw new ResourceNotFoundException(objectName);
                }
            }
        } else {
            throw new NoneSpecifiedException();
        }

        return stream;
    }

    TupleDefinition getDefinition() {
        return definition;
    }
}
