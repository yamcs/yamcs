package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.OutputStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class CreateOutputStreamStatement extends SimpleStreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("port", DataType.INT);
    }

    String streamName;
    StreamExpression expression;

    public CreateOutputStreamStatement(String streamName, StreamExpression expression) {
        this.streamName = streamName;
        this.expression = expression;
    }

    @Override
    public void execute(ExecutionContext c, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = c.getDb();
        expression.bind(c);

        Stream s = expression.execute(c);

        OutputStream os = null;
        synchronized (ydb) {
            if (ydb.streamOrTableExists(streamName)) {
                throw new ResourceAlreadyExistsException(streamName);
            }
            try {
                os = new OutputStream(ydb, streamName, s.getDefinition());
                ydb.addStream(os);
                s.addSubscriber(os);
                os.setSubscribedStream(s);

                if (s.getState() == Stream.SETUP) {
                    s.start();
                }
            } catch (YarchException e) {
                if (os != null) {
                    os.close();
                }
                throw new GenericStreamSqlException(e.getMessage());
            }

            Tuple tuple = new Tuple(TDEF, new Object[] { os.getPort() });
            consumer.accept(tuple);
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
