package org.yamcs.yarch.streamsql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.YarchDatabase;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.MergeStatement;
import org.yamcs.yarch.streamsql.NotImplementedException;
import org.yamcs.yarch.streamsql.StreamExpression;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;


public class MergeStatement extends StreamSqlStatement {
    static Logger log=LoggerFactory.getLogger(MergeStatement.class.getName());
    
    public MergeStatement(StreamExpression expr1, StreamExpression expr2, String name) {
        // TODO Auto-generated constructor stub
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        synchronized(dict) {
            log.warn("Merge statement not yet implemented");
            throw new NotImplementedException("Merge statement");
        }
    }

}
