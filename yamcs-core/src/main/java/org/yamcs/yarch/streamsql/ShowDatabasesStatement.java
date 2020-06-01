package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

public class ShowDatabasesStatement implements StreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("database", DataType.STRING);
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        List<String> databases = new ArrayList<>(YarchDatabase.getDatabases());
        Collections.sort(databases);

        for (String database : databases) {
            Tuple tuple = new Tuple(TDEF, new Object[] { database });
            resultListener.next(tuple);
        }
        resultListener.complete();
    }
}
