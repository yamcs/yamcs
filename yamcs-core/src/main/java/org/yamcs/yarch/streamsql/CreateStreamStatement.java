package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.InternalStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class CreateStreamStatement extends SimpleStreamSqlStatement {

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
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance db = context.getDb();
        synchronized (db) {
            if (db.streamOrTableExists(streamName)) {
                throw new ResourceAlreadyExistsException(streamName);
            }

            Stream stream;
            if (expression != null) {
                expression.bind(context);
                stream = expression.execute(context);
                stream.setName(streamName);
            } else {
                stream = new InternalStream(db, streamName, tupleDefinition);
            }
            try {
                db.addStream(stream);
            } catch (YarchException e) {
                throw new GenericStreamSqlException(e.getMessage());
            }
        }
    }
}
