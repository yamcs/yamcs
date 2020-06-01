package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowTablesStatement implements StreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("name", DataType.STRING);
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        synchronized (dict) {
            List<TableDefinition> tdefs = new ArrayList<>(dict.getTableDefinitions());
            Collections.sort(tdefs, (t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
            for (TableDefinition td : tdefs) {
                Tuple tuple = new Tuple(TDEF, new Object[] { td.getName() });
                resultListener.next(tuple);
            }
        }
        resultListener.complete();
    }
}
