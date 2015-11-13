package org.yamcs.yarch.streamsql;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.MergeStream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

class MergeExpression implements StreamExpression {
    ArrayList<TupleSourceExpression> sources=new ArrayList<TupleSourceExpression> ();
    String mergeColumn;
    boolean ascending=true;
    static Logger log=LoggerFactory.getLogger(MergeExpression.class.getName());

    public void setMergeColumn(String name) {
        mergeColumn=name;
    }
    
    public void setAscending(boolean ascending) {
        this.ascending = ascending;
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
            AbstractStream ms=new MergeStream(dict,streams,mergeColumn, ascending);
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
