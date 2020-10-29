package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.Tuple;

public class DeleteStatement extends SimpleStreamSqlStatement {

    public DeleteStatement(String tableName, Expression expression) {
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        // TODO Auto-generated method stub
        
    }

}
