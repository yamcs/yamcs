package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.yarch.YarchDatabase;

public class ShowDatabasesStatement extends StreamSqlStatement {

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        List<String> databases = new ArrayList<>(YarchDatabase.getDatabases());
        Collections.sort(databases);

        StreamSqlResult res = new StreamSqlResult();
        res.setHeader("database");
        for (String database : databases) {
            res.addRow(database);
        }
        return res;
    }
}
