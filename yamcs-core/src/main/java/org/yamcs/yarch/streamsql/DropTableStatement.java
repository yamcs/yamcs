package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class DropTableStatement implements StreamSqlStatement {

    boolean ifExists;
    String tblName;

    public DropTableStatement(boolean ifExists, String name) {
        this.ifExists = ifExists;
        this.tblName = name;
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(c.getDbName());
        try {
            synchronized (ydb) {
                if (!ifExists || ydb.getTable(tblName) != null) {
                    ydb.dropTable(tblName);
                }
            }
        } catch (YarchException e) {
            throw new GenericStreamSqlException(e.getMessage());
        }
        resultListener.complete();
    }
}
