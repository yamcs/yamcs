package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowStreamStatement implements StreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("column", DataType.STRING);
        TDEF.addColumn("type", DataType.STRING);
    }

    String name;
    boolean showPort;

    public ShowStreamStatement(String name, boolean showPort) {
        this.name = name;
        this.showPort = showPort;
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        Stream s = null;
        synchronized (dict) {
            s = dict.getStream(name);
        }
        if (s == null) {
            throw new ResourceNotFoundException(name);
        }

        if (showPort) {
            throw new UnsupportedOperationException();
            /*
            final int port;
            if (s instanceof OutputStream) {
                port = ((OutputStream) s).getPort();
            } else {
                throw new NotAStreamException(name);
            }
            
            return new StreamSqlResult() {
                @Override
                public String toString() {
                    return "port=" + port;
                };
            };*/
        } else {
            for (ColumnDefinition cdef : s.getDefinition().getColumnDefinitions()) {
                Tuple tuple = new Tuple(TDEF, new Object[] {
                        cdef.getName(),
                        cdef.getType().toString(),
                });
                resultListener.next(tuple);
            }
            resultListener.complete();
        }
    }
}
