package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.InternalStream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.StreamAlreadyExistsException;
import org.yamcs.yarch.streamsql.StreamExpression;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

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
                throw new StreamAlreadyExistsException("a stream or table '"+streamName+"' already exists");

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
