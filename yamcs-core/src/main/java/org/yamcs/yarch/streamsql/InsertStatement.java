package org.yamcs.yarch.streamsql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchException;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.InsertStatement;
import org.yamcs.yarch.streamsql.NotImplementedException;
import org.yamcs.yarch.streamsql.ResourceNotFoundException;
import org.yamcs.yarch.streamsql.StreamExpression;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class InsertStatement extends StreamSqlStatement {
    String name;
    StreamExpression expression;
    static Logger log = LoggerFactory.getLogger(InsertStatement.class.getName());
    InsertMode insertMode;

    public InsertStatement(String name, StreamExpression expression, InsertMode mode) {
        this.name = name;
        this.expression = expression;
        this.insertMode = mode;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(c.getDbName());

        TableDefinition outputTableDef = ydb.getTable(name);
        Stream outputStream = outputTableDef == null ? ydb.getStream(name) : null;

        if (outputTableDef == null && outputStream == null) {
            throw new ResourceNotFoundException(name);
        }

        expression.bind(c);
        Stream inputStream = expression.execute(c);

        if (outputTableDef != null) {
            try {
                // writing into a table
                TableWriter tableWriter = ydb.getStorageEngine(outputTableDef)
                        .newTableWriter(ydb, outputTableDef, insertMode);
                inputStream.addSubscriber(tableWriter);
                tableWriter.closeFuture().thenAccept(v -> inputStream.removeSubscriber(tableWriter));
            } catch (YarchException e) {
                log.warn("Got exception when creatin table", e);
                throw new GenericStreamSqlException(e.getMessage());
            }
        } else {
            inputStream.addSubscriber(new StreamSubscriber() {
                @Override
                public void streamClosed(Stream stream) {
                }

                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    outputStream.emitTuple(tuple);
                }
            });

        }
        return new StreamSqlResult();
    }
}
