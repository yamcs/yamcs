package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class CreateInputStreamStatement extends SimpleStreamSqlStatement {

    TupleDefinition definition;
    String streamName;

    public CreateInputStreamStatement(String name, TupleDefinition definition) {
        this.definition = definition;
        this.streamName = name;
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        throw new StreamSqlException(ErrCode.NOT_IMPLEMENTED, "InputStream not implemented");
    }
}
