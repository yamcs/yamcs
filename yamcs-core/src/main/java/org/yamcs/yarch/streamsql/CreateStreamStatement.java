package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.InternalStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class CreateStreamStatement implements StreamSqlStatement {

    String streamName;
    StreamExpression expression;
    TupleDefinition tupleDefinition;

    public CreateStreamStatement(String streamName, StreamExpression expression) {
        this.streamName = streamName;
        this.expression = expression;
    }

    public CreateStreamStatement(String name, TupleDefinition tupleDefinition) {
        this.streamName = name;
        this.tupleDefinition = tupleDefinition;
    }

    @Override
    public void execute(ExecutionContext c, ResultListener listener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        synchronized (dict) {
            if (dict.streamOrTableExists(streamName)) {
                throw new StreamAlreadyExistsException(streamName);
            }

            Stream stream;
            if (expression != null) {
                expression.bind(c);
                stream = expression.execute(c);
                stream.setName(streamName);
            } else {
                stream = new InternalStream(dict, streamName, tupleDefinition);
            }
            try {
                dict.addStream(stream);
            } catch (YarchException e) {
                throw new GenericStreamSqlException(e.getMessage());
            }
            listener.complete();
        }
    }
}
