package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.List;

/**
 * @see org.yamcs.yarch.streamsql.SelectExpression
 *
 */
public class SelectStream extends AbstractStream implements StreamSubscriber {
    CompiledExpression whereExp;
    AbstractStream input;
    final private List<CompiledExpression> aggInputList;
    final private List<CompiledExpression> selectList;
    final private WindowProcessor windowProc;
    final private boolean hasStars;
    
    //used as a marker for the * in "select a,*,b from..." expressions
    final static public CompiledExpression STAR=new CompiledExpression() {
        @Override
        public Object getValue(Tuple tuple) {
            return null;
        }
        @Override
        public ColumnDefinition getDefinition() {
            return null;
        }
    };

    /**
     * 
     * @param ydb
     * @param input
     * @param cWhereClause if null, then no where filtering
     * @param wp if null, then no windowProcessing (aggInputList is also null in this case)
     * @param cselectList
     * @param outputDef //output definition containing the expanded stars
     * @param minOutputDef //output definition where stars are not included
     */
    public SelectStream(YarchDatabase ydb, AbstractStream input, CompiledExpression cWhereClause, List<CompiledExpression> caggInputList, WindowProcessor wp,
            List<CompiledExpression> cselectList, TupleDefinition outputDef, TupleDefinition minOutputDef) {

        super(ydb, input.getName()+"_select", outputDef);
        this.input=input;
        input.addSubscriber(this);
        
        this.aggInputList=caggInputList;
        this.whereExp=cWhereClause;
        this.windowProc=wp;
        this.selectList=cselectList;
        boolean hs=false;
        if(selectList!=null) {
            for(CompiledExpression ce:selectList) {
                if(ce==STAR) {
                    hs=true;
                    break;
                }
            }
        }
        hasStars=hs;
    }

    @Override
    public void onTuple(Stream stream, Tuple t) {
        if(whereExp!=null) {
            Boolean v=(Boolean)whereExp.getValue(t);
            if(!v) return;
        }
        if(windowProc!=null) {
            processWindow(t);
        } else {
            processSelectList(t);
        }
    }

    private void processWindow(Tuple tuple) {
        if(aggInputList!=null) {
            Object[] v=new Object[aggInputList.size()];
            for(int i=0;i<v.length;i++) {
                v[i]=aggInputList.get(i).getValue(tuple);
            }
            tuple=new Tuple(windowProc.aggInputDef,v);
        }
        for(Tuple t:windowProc.newData(tuple)) {
            processSelectList(t);
        }

    }

    private void processSelectList(Tuple tuple) {
        if(selectList==null) {
            emitTuple(tuple);
            return;
        }
        ArrayList<Object> v=new ArrayList<Object>();
        TupleDefinition tdef=new TupleDefinition();
        for(CompiledExpression ce:selectList) {
            if(ce==STAR) {
                for(int i=0;i<tuple.size();i++) {
                    tdef.addColumn(tuple.getColumnDefinition(i));
                    v.add(tuple.getColumn(i));
                }
            } else {
                tdef.addColumn(ce.getDefinition());
                v.add(ce.getValue(tuple));
            }
        }
        tuple=new Tuple(tdef, v);
        emitTuple(tuple);
    }

    @Override
    public void streamClosed(Stream stream) {
        close();
    }

    @Override
    public void start() {
        if(input.state==SETUP) input.start();
        state=RUNNING;
    }

    @Override
    protected void doClose() {
        input.close(); //TODO replace with removeSubscriber
    }
}