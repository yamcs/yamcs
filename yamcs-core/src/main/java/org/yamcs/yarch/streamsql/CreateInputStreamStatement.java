package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class CreateInputStreamStatement implements StreamSqlStatement {

    TupleDefinition definition;
    String streamName;

    public CreateInputStreamStatement(String name, TupleDefinition definition) {
        this.definition = definition;
        this.streamName = name;
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        throw new StreamSqlException(ErrCode.NOT_IMPLEMENTED, "InputStream not implemented");
    }
}
