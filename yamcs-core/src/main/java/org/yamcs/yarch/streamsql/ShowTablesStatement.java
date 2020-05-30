package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.utils.ValueHelper;
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
            List<TableDefinition> tdefs = new ArrayList<>(dict.getTableDefinitions());
            Collections.sort(tdefs, (t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
            for (TableDefinition td : tdefs) {
                res.addRow(ValueHelper.newValue(td.getName()));
            }
        }
        return res;
    }
}
