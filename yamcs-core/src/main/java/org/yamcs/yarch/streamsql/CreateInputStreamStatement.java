package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.InputStream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.StreamAlreadyExistsException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class CreateInputStreamStatement extends StreamSqlStatement {
    TupleDefinition definition;
    String streamName;

    public CreateInputStreamStatement(String name, TupleDefinition definition) {
        this.definition=definition;
        this.streamName=name;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        synchronized(dict) {
            InputStream stream=null;
            if(dict.streamOrTableExists(streamName)) {
                throw new StreamAlreadyExistsException(streamName);
            }
            try {
                stream=new InputStream(dict, streamName, definition);
                dict.addStream(stream);
                stream.start();
                return new StreamSqlResult("port",stream.getPort());
            } catch (YarchException e) {
                if(stream!=null) stream.close();
                throw new GenericStreamSqlException("Cannot create input stream: "+e.getMessage());
            }
        }
    }
}
