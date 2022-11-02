package org.yamcs.yarch.streamsql;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.MutableLong;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class InsertStatement extends SimpleStreamSqlStatement {
    String name;
    StreamExpression expression;
    static Logger log = LoggerFactory.getLogger(InsertStatement.class.getName());
    InsertMode insertMode;

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("inserted", DataType.LONG);
    }


    public InsertStatement(String name, StreamExpression expression, InsertMode mode) {
        this.name = name;
        this.expression = expression;
        this.insertMode = mode;
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();

        TableDefinition outputTableDef = ydb.getTable(name);
        Stream outputStream = outputTableDef == null ? ydb.getStream(name) : null;

        if (outputTableDef == null && outputStream == null) {
            throw new ResourceNotFoundException(name);
        }

        expression.bind(context);
        Stream inputStream = expression.execute(context);

        if (outputTableDef != null) {
            try {
                // writing into a table
                TableWriter tableWriter = ydb.getStorageEngine(outputTableDef)
                        .newTableWriter(ydb, outputTableDef, insertMode);
                inputStream.addSubscriber(tableWriter);
                tableWriter.closeFuture().thenAccept(v -> inputStream.removeSubscriber(tableWriter));
            } catch (YarchException e) {
                log.warn("Exception while inserting into table", e);
                throw new GenericStreamSqlException(e.getMessage());
            }
        } else {
            inputStream.addSubscriber(new StreamSubscriber() {
                @Override
                public void streamClosed(Stream stream) {
                    log.debug("InputStream {} closed", stream.getName());
                }

                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    outputStream.emitTuple(tuple);
                }
            });
        }

        Long inserted = null;
        if (expression.isFinite()) {
            Semaphore semaphore = new Semaphore(0);
            MutableLong count = new MutableLong(0);
            inputStream.addSubscriber(new StreamSubscriber() {
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    count.increment();
                }

                @Override
                public void streamClosed(Stream stream) {
                    semaphore.release();
                }
            });

            inputStream.start();
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new GenericStreamSqlException("Interrupted");
            }
            inserted = count.getLong();
        } else {
            inputStream.start();
        }

        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn("inserted", DataType.LONG);
        consumer.accept(new Tuple(tdef, Arrays.asList(inserted)));
    }

    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
