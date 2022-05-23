package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class DropTableStatement extends SimpleStreamSqlStatement {

    boolean ifExists;
    String tblName;

    public DropTableStatement(boolean ifExists, String name) {
        this.ifExists = ifExists;
        this.tblName = name;
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        try {
            synchronized (ydb) {
                if (!ifExists || ydb.getTable(tblName) != null) {
                    ydb.dropTable(tblName);
                }
            }
        } catch (YarchException e) {
            throw new GenericStreamSqlException(e.getMessage());
        }
    }

}
