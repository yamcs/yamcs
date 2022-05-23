package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;

public class MergeStatement extends SimpleStreamSqlStatement {

    static Logger log = LoggerFactory.getLogger(MergeStatement.class.getName());

    public MergeStatement(StreamExpression expr1, StreamExpression expr2, String name) {
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        synchronized (ydb) {
            log.warn("Merge statement not yet implemented");
            throw new NotImplementedException("Merge statement");
        }        
    }
}
