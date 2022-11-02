package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class DescribeStatement extends SimpleStreamSqlStatement {

    private static final TupleDefinition TDEF_TABLE = new TupleDefinition();
    static {
        TDEF_TABLE.addColumn("column", DataType.STRING);
        TDEF_TABLE.addColumn("type", DataType.STRING);
        TDEF_TABLE.addColumn("key", DataType.STRING);
        TDEF_TABLE.addColumn("extra", DataType.STRING);
    }

    private static final TupleDefinition TDEF_STREAM = new TupleDefinition();
    static {
        TDEF_STREAM.addColumn("column", DataType.STRING);
        TDEF_STREAM.addColumn("type", DataType.STRING);
    }

    private String objectName;

    public DescribeStatement(String objectName) {
        this.objectName = objectName;
    }

    @Override
    public void execute(ExecutionContext c, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = c.getDb();

        TableDefinition tdef = null;
        Stream stream = null;
        synchronized (ydb) {
            tdef = ydb.getTable(objectName);
            stream = ydb.getStream(objectName);
        }
        if (tdef != null) {
            describeTable(tdef, consumer);
        } else if (stream != null) {
            describeStream(stream, consumer);
        } else {
            throw new ResourceNotFoundException(objectName);
        }
    }

    private void describeTable(TableDefinition tdef, Consumer<Tuple> consumer) {
        for (var cdef : tdef.getKeyDefinition()) {
            var tuple = new Tuple(TDEF_TABLE, new Object[] {
                    cdef.getName(),
                    cdef.getType().toString(),
                    "*",
                    cdef.isAutoIncrement() ? "auto_increment" : "",
            });
            consumer.accept(tuple);
        }
        for (var cdef : tdef.getValueDefinition()) {
            var tuple = new Tuple(TDEF_TABLE, new Object[] {
                    cdef.getName(),
                    cdef.getType().toString(),
                    "",
                    cdef.isAutoIncrement() ? "auto_increment" : "",
            });
            consumer.accept(tuple);
        }
    }

    private void describeStream(Stream stream, Consumer<Tuple> consumer) {
        for (ColumnDefinition cdef : stream.getDefinition().getColumnDefinitions()) {
            Tuple tuple = new Tuple(TDEF_STREAM, new Object[] {
                    cdef.getName(),
                    cdef.getType().toString(),
            });
            consumer.accept(tuple);
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF_TABLE;
    }
}
