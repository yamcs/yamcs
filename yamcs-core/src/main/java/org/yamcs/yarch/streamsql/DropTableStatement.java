package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;


public class DropTableStatement extends StreamSqlStatement {
    String tblName;

    public DropTableStatement(String name) {
        this.tblName=name;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        try {
            synchronized(dict) {
                dict.dropTable(tblName);
            }
        } catch (YarchException e) {
            throw new GenericStreamSqlException(e.getMessage());
        }
        return new StreamSqlResult();
    }
}
