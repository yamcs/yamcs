package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.TupleDefinition;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class CreateInputStreamStatement extends StreamSqlStatement {
    TupleDefinition definition;
    String streamName;

    public CreateInputStreamStatement(String name, TupleDefinition definition) {
        this.definition=definition;
        this.streamName=name;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        throw new StreamSqlException(ErrCode.NOT_IMPLEMENTED, "InputStream not implemented");
    }
}
