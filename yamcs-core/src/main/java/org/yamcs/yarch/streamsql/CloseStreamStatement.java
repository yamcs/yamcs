package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class CloseStreamStatement implements StreamSqlStatement {

    String name;

    public CloseStreamStatement(String name) {
        this.name = name;
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        // locking of the dictionary is performed inside the close
        Stream s = dict.getStream(name);
        if (s == null) {
            throw new ResourceNotFoundException(name);
        }
        s.close();
        resultListener.complete();
    }
}
