package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class DescribeStatement implements StreamSqlStatement {

    private static final TupleDefinition TDEF_TABLE = new TupleDefinition();
    static {
        TDEF_TABLE.addColumn("column", DataType.STRING);
        TDEF_TABLE.addColumn("type", DataType.STRING);
        TDEF_TABLE.addColumn("key", DataType.STRING);
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
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());

        TableDefinition tdef = null;
        Stream stream = null;
        synchronized (dict) {
            tdef = dict.getTable(objectName);
            stream = dict.getStream(objectName);
        }
        if (tdef != null) {
            describeTable(tdef, resultListener);
        } else if (stream != null) {
            describeStream(stream, resultListener);
        } else {
            throw new ResourceNotFoundException(objectName);
        }
    }

    private void describeTable(TableDefinition tdef, ResultListener resultListener) {
        for (ColumnDefinition cdef : tdef.getKeyDefinition().getColumnDefinitions()) {
            Tuple tuple = new Tuple(TDEF_TABLE, new Object[] {
                    cdef.getName(),
                    cdef.getType().toString(),
                    "*"
            });
            resultListener.next(tuple);
        }
        for (ColumnDefinition cdef : tdef.getValueDefinition().getColumnDefinitions()) {
            Tuple tuple = new Tuple(TDEF_TABLE, new Object[] {
                    cdef.getName(),
                    cdef.getType().toString(),
                    null
            });
            resultListener.next(tuple);
        }
        resultListener.complete();
    }

    private void describeStream(Stream stream, ResultListener resultListener) {
        for (ColumnDefinition cdef : stream.getDefinition().getColumnDefinitions()) {
            Tuple tuple = new Tuple(TDEF_STREAM, new Object[] {
                    cdef.getName(),
                    cdef.getType().toString(),
            });
            resultListener.next(tuple);
        }
        resultListener.complete();
    }
}
