package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.OutputStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowStreamStatement extends StreamSqlStatement {

    String name;
    boolean showPort;

    public ShowStreamStatement(String name, boolean showPort) {
        this.name = name;
        this.showPort = showPort;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {

        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        Stream s = null;
        synchronized (dict) {
            s = dict.getStream(name);
        }
        if (s == null) {
            throw new ResourceNotFoundException(name);
        }

        if (showPort) {
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
            };
        } else {
            StreamSqlResult res = new StreamSqlResult();
            res.setHeader("column", "type");
            for (ColumnDefinition cdef : s.getDefinition().getColumnDefinitions()) {
                res.addRow(cdef.getName(), cdef.getType().toString());
            }
            return res;
        }
    }
}
