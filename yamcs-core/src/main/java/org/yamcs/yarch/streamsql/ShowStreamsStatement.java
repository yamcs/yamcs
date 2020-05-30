package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowStreamsStatement implements StreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("name", DataType.STRING);
        TDEF.addColumn("emitted", DataType.LONG);
        TDEF.addColumn("subscribers", DataType.INT);
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        synchronized (dict) {
            List<Stream> streams = new ArrayList<>(dict.getStreams());
            Collections.sort(streams, (s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
            for (Stream stream : streams) {
                Tuple tuple = new Tuple(TDEF, new Object[] {
                        stream.getName(),
                        stream.getDataCount(),
                        stream.getSubscriberCount(),
                });
                resultListener.next(tuple);
            }
        }
        resultListener.complete();
    }
}
