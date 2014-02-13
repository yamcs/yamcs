package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.MergeStream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.MergeExpression;
import org.yamcs.yarch.streamsql.StreamExpression;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.TupleSourceExpression;

class MergeExpression implements StreamExpression {
    ArrayList<TupleSourceExpression> sources=new ArrayList<TupleSourceExpression> ();
    String mergeColumn;
    static Logger log=LoggerFactory.getLogger(MergeExpression.class.getName());

    public void setMergeColumn(String name) {
        mergeColumn=name;
    }
    
    @Override
    public void bind(ExecutionContext c) throws StreamSqlException {
        for(TupleSourceExpression tps:sources) {
            tps.bind(c);
        }
    }
    
    @Override
    public AbstractStream execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        AbstractStream[] streams=new AbstractStream[sources.size()];
        for(int i=0;i<streams.length;i++) {
            streams[i]=sources.get(i).execute(c);
        }
        if(streams.length==1) return streams[0];
        else {
            AbstractStream ms=new MergeStream(dict,streams,mergeColumn);
            return ms;
        }
    }

    public void addTupleSourceExpression(TupleSourceExpression tsrc) {
        sources.add(tsrc);
    }

  
    @Override
    public TupleDefinition getOutputDefinition() {
        return null;
    }

}
