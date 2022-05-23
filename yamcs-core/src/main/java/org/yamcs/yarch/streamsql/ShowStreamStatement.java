package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowStreamStatement extends SimpleStreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("column", DataType.STRING);
        TDEF.addColumn("type", DataType.STRING);
    }

    String name;

    public ShowStreamStatement(String name) {
        this.name = name;
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        Stream s = null;
        synchronized (ydb) {
            s = ydb.getStream(name);
        }
        if (s == null) {
            throw new ResourceNotFoundException(name);
        }

        for (ColumnDefinition cdef : s.getDefinition().getColumnDefinitions()) {
            Tuple tuple = new Tuple(TDEF, new Object[] {
                    cdef.getName(),
                    cdef.getType().toString(),
            });
            consumer.accept(tuple);
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
