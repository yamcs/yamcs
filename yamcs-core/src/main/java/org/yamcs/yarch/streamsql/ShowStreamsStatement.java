package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowStreamsStatement extends SimpleStreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("name", DataType.STRING);
        TDEF.addColumn("emitted", DataType.LONG);
        TDEF.addColumn("subscribers", DataType.INT);
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        synchronized (ydb) {
            List<Stream> streams = new ArrayList<>(ydb.getStreams());
            Collections.sort(streams, (s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
            for (Stream stream : streams) {
                Tuple tuple = new Tuple(TDEF, new Object[] {
                        stream.getName(),
                        stream.getDataCount(),
                        stream.getSubscriberCount(),
                });
                consumer.accept(tuple);
            }
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
