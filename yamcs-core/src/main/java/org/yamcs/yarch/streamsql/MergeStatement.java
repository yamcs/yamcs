package org.yamcs.yarch.streamsql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class MergeStatement implements StreamSqlStatement {

    static Logger log = LoggerFactory.getLogger(MergeStatement.class.getName());

    public MergeStatement(StreamExpression expr1, StreamExpression expr2, String name) {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        synchronized (dict) {
            log.warn("Merge statement not yet implemented");
            throw new NotImplementedException("Merge statement");
        }
    }
}
