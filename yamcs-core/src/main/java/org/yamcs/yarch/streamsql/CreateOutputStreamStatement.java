package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.OutputStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.StreamAlreadyExistsException;
import org.yamcs.yarch.streamsql.StreamExpression;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class CreateOutputStreamStatement extends StreamSqlStatement {
    String streamName;
    StreamExpression expression;
    public CreateOutputStreamStatement(String streamName, StreamExpression expression) {
        this.streamName=streamName;
        this.expression=expression;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        expression.bind(c);

        Stream s=expression.execute(c);

        OutputStream os=null;
        synchronized(dict) {
            if(dict.streamOrTableExists(streamName)) {
                throw new StreamAlreadyExistsException(streamName);
            }
            try {
                os=new OutputStream(dict, streamName,s.getDefinition());
                dict.addStream(os);
                s.addSubscriber(os);
                os.setSubscribedStream(s);

                if(s.getState()==Stream.SETUP) s.start();
            } catch (YarchException e) {
                if(os!=null)os.close();
                throw new GenericStreamSqlException(e.getMessage());
            }
            return new StreamSqlResult("port",os.getPort());
        }
    }
}