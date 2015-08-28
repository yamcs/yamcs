package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;


public class DropTableStatement extends StreamSqlStatement {
    boolean ifExists;
    String tblName;

    public DropTableStatement(boolean ifExists, String name) {
        this.ifExists=ifExists;
        this.tblName=name;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase ydb=YarchDatabase.getInstance(c.getDbName());
        try {
            synchronized(ydb) {
                if (!ifExists || ydb.getTable(tblName)!=null) {
                    ydb.dropTable(tblName);
                }
            }
        } catch (YarchException e) {
            throw new GenericStreamSqlException(e.getMessage());
        }
        return new StreamSqlResult();
    }
}
