package org.yamcs.yarch.streamsql;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.yamcs.logging.Log;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class SelectTableStatement implements StreamSqlStatement {

    private SelectExpression expression;
    static final Tuple END_SIGNAL = new Tuple(new TupleDefinition(), Arrays.asList());

    public SelectTableStatement(SelectExpression expression) {
        this.expression = expression;
    }

    @Override
    public void execute(YarchDatabaseInstance ydb, ResultListener resultListener, long limit)
            throws StreamSqlException {
        if (resultListener == null) {
            throw new GenericStreamSqlException("Cannot select without a result listener");
        }
        ExecutionContext context = new ExecutionContext(ydb);

        Stream stream = createStream(context);
        resultListener.start(stream.getDefinition());

        stream.addSubscriber(new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                resultListener.next(tuple);
                if (stream.getDataCount() >= limit) {
                    stream.close();
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                resultListener.complete();
                context.close();
            }
        });
        stream.start();
    }

    @Override
    public StreamSqlResult execute(YarchDatabaseInstance ydb) throws StreamSqlException {
        ExecutionContext context = new ExecutionContext(ydb);
        Stream stream = createStream(context);

        QueueStreamSqlResult result = new QueueStreamSqlResult(context, stream);
        stream.addSubscriber(result);
        stream.start();
        stream.addSubscriber(new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
            }

            @Override
            public void streamClosed(Stream stream) {
                context.close();
            }
        });
        return result;
    }

    Stream createStream(ExecutionContext context) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        String tblName = expression.tupleSourceExpression.objectName;
        if (ydb.getTable(tblName) == null) {
            throw new GenericStreamSqlException(String.format("Object %s does not exist or is not a table", tblName));
        }

        expression.bind(context);
        return expression.execute(context);
    }

    static class QueueStreamSqlResult implements StreamSqlResult, StreamSubscriber {
        final Stream stream;
        final ExecutionContext context;

        BlockingQueue<Tuple> queue = new ArrayBlockingQueue<Tuple>(1024);
        Tuple next;
        static Log log = new Log(QueueStreamSqlResult.class);

        QueueStreamSqlResult(ExecutionContext context, Stream stream) {
            this.stream = stream;
            this.context = context;
        }

        @Override
        public boolean hasNext() {
            if (next == null) {
                next = queueTake();
            }

            if (next == END_SIGNAL) {
                return false;
            } else {
                return true;
            }
        }

        @Override
        public Tuple next() {
            if (next == null) {
                next = queueTake();
            }
            if (next == END_SIGNAL) {
                throw new NoSuchElementException();
            }

            Tuple r = next;
            next = null;

            return r;
        }

        private Tuple queueTake() {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return END_SIGNAL;
        }

        @Override
        public void close() {
            stream.close();
            context.close();
            queue.add(END_SIGNAL);
        }

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            queue.add(tuple);
        }

        @Override
        public void streamClosed(Stream stream) {
            queue.add(END_SIGNAL);
        }

        @Override
        protected void finalize() {
            if (!stream.isClosed()) {
                log.error("Stream {} left dangling (StreamSqlResult has been discarded before closing)",
                        stream.getName());
                close();
            }
        }
    }

}
