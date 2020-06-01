package org.yamcs.yarch.streamsql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class InsertStatement implements StreamSqlStatement {

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
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
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
                log.warn("Exception while creating table", e);
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
        resultListener.complete();
    }
}
