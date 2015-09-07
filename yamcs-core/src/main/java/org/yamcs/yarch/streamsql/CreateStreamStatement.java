package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.InternalStream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

public class CreateStreamStatement extends StreamSqlStatement {
    String streamName;
    StreamExpression expression;
    TupleDefinition tupleDefinition;

    public CreateStreamStatement(String streamName, StreamExpression expression) {
        this.streamName=streamName;
        this.expression=expression;
    }

    public CreateStreamStatement(String name, TupleDefinition tupleDefinition) {
        this.streamName=name;
        this.tupleDefinition=tupleDefinition;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        synchronized(dict) {
            if(dict.streamOrTableExists(streamName))
                throw new StreamAlreadyExistsException(streamName);

            AbstractStream stream;
            if(expression!=null) { 
                expression.bind(c);
                stream=expression.execute(c);
                stream.setName(streamName);
            } else {
                stream=new InternalStream(dict, streamName, tupleDefinition);
            }
            try {
              //  System.out.println("adding stream "+stream+" to the dictionary");
                dict.addStream(stream);
            } catch (YarchException e) {
                throw new GenericStreamSqlException(e.getMessage());
            }
            return new StreamSqlResult();
        } 
    }
}
