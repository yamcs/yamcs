package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.ResourceNotFoundException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;


public class CloseStreamStatement extends StreamSqlStatement {
    String name;

    public CloseStreamStatement(String name) {
        this.name=name;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        //locking of the dictionary is performed inside the close
        Stream s=dict.getStream(name);
        if(s==null) throw new ResourceNotFoundException(name);
        s.close();
        return new StreamSqlResult();
    }
}
