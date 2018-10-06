package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowStreamsStatement extends StreamSqlStatement {

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        StreamSqlResult res = new StreamSqlResult();
        res.setHeader("name", "emitted", "subscribers");
        synchronized (dict) {
            List<Stream> streams = new ArrayList<>(dict.getStreams());
            Collections.sort(streams, (s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
            for (Stream stream : streams) {
                res.addRow(stream.getName(), stream.getDataCount(), stream.getSubscriberCount());
            }
        }
        return res;
    }
}
