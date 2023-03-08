package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;

public class CloseStreamStatement extends SimpleStreamSqlStatement  {

    String name;

    public CloseStreamStatement(String name) {
        this.name = name;
    }

    @Override
    public void execute(ExecutionContext c, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance db = c.getDb();
        // locking of the dictionary is performed inside the close
        Stream s = db.getStream(name);
        if (s == null) {
            throw new ResourceNotFoundException(name);
        }
        s.close();
    }

}
