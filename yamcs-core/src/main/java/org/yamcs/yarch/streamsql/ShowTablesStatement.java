package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowTablesStatement extends StreamSqlStatement {

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        StreamSqlResult res = new StreamSqlResult();
        res.setHeader("name");
        synchronized (dict) {
            for (TableDefinition td : dict.getTableDefinitions()) {
                res.addRow(td.getName());
            }
        }
        return res;
    }
}
