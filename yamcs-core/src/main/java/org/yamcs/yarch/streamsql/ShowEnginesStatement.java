package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.yarch.YarchDatabase;

public class ShowEnginesStatement extends StreamSqlStatement {

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        List<String> engines = new ArrayList<>(YarchDatabase.getStorageEngineNames());
        Collections.sort(engines);

        StreamSqlResult res = new StreamSqlResult();
        res.setHeader("engine", "default");
        for (String engine : engines) {
            String def = engine.equals(YarchDatabase.getDefaultStorageEngineName()) ? "*" : null;
            res.addRow(engine, def);
        }
        return res;
    }
}
