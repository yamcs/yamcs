package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowTablesStatement extends SimpleStreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("name", DataType.STRING);
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        synchronized (ydb) {
            List<TableDefinition> tdefs = new ArrayList<>(ydb.getTableDefinitions());
            Collections.sort(tdefs, (t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
            for (TableDefinition td : tdefs) {
                Tuple tuple = new Tuple(TDEF, new Object[] { td.getName() });
                consumer.accept(tuple);
            }
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
