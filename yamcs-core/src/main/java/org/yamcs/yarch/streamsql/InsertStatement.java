package org.yamcs.yarch.streamsql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.YarchException;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.InsertStatement;
import org.yamcs.yarch.streamsql.NotImplementedException;
import org.yamcs.yarch.streamsql.ResourceNotFoundException;
import org.yamcs.yarch.streamsql.StreamExpression;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class InsertStatement extends StreamSqlStatement {
    String name;
    StreamExpression expression;
    static Logger log=LoggerFactory.getLogger(InsertStatement.class.getName());
    InsertMode insertMode;
    
    public InsertStatement(String name, StreamExpression expression, InsertMode mode) {
        this.name=name;
        this.expression=expression;
        this.insertMode=mode;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase ydb=YarchDatabase.getInstance(c.getDbName());
        synchronized(ydb) {
            TableDefinition outputTableDef=null;
            TupleDefinition outputTuple;
            Stream outputStream=null;
            if((outputTableDef=ydb.getTable(name))!=null) {
                outputTuple=outputTableDef.getTupleDefinition();
            } else if((outputStream=ydb.getStream(name))!=null) {
                outputTuple=outputStream.getDefinition();
            } else {
                throw new ResourceNotFoundException(name);	
            }
            expression.bind(c);
            Stream inputStream=expression.execute(c);
            TupleDefinition inputTuple=inputStream.getDefinition();
            // compatibility check disabled since we switched to the dynamic schema
       //     String reason=TupleDefinition.checkCompatibility(inputTuple, outputTuple); 
       //     if(reason!=null) throw new IncompatibilityException(reason);

            if(outputTableDef!=null) {
                try {
                    //writing into a table
                    TableWriter tableWriter=ydb.getStorageEngine(outputTableDef).newTableWriter(outputTableDef, insertMode);
                    inputStream.addSubscriber(tableWriter);
                    return new StreamSqlResult();
                } catch(YarchException e) {
                    log.warn("Got exception when creatin table", e);
                    throw new GenericStreamSqlException(e.getMessage());
                }
            }
        }
        log.warn("Insert into streams not yet implemented");
        throw new NotImplementedException("Inserting into streams");
    }
}
