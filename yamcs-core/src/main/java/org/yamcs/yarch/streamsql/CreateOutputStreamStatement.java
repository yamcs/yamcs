package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.OutputStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class CreateOutputStreamStatement implements StreamSqlStatement {

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
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        expression.bind(c);

        Stream s = expression.execute(c);

        OutputStream os = null;
        synchronized (dict) {
            if (dict.streamOrTableExists(streamName)) {
                throw new StreamAlreadyExistsException(streamName);
            }
            try {
                os = new OutputStream(dict, streamName, s.getDefinition());
                dict.addStream(os);
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
            resultListener.next(tuple);
            resultListener.complete();
        }
    }
}
