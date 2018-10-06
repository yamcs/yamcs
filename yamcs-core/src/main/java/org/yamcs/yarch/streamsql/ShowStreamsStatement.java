package org.yamcs.yarch.streamsql;

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
            for (Stream stream : dict.getStreams()) {
                res.addRow(stream.getName(), stream.getDataCount(), stream.getSubscriberCount());
            }
        }
        return res;
    }
}
