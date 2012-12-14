package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.yarch.streamsql.WindowSpecification;


public abstract class WindowProcessor {
    List<CompiledAggregateExpression> aggList;
    List<Tuple> windowTuples=new ArrayList<Tuple>();;
    boolean noOverlap; //if there is no overlap between the windows, some optimizations are possible
    TupleDefinition aggOutputDef;
    final private List<Tuple> emptyReturn=new ArrayList<Tuple>(0);
    public TupleDefinition aggInputDef;
    
    public static WindowProcessor getInstance(WindowSpecification spec, TupleDefinition aggInputDef, List<CompiledAggregateExpression> aggList, TupleDefinition aggregateOutputDef) {
        WindowProcessor wp=null;
        switch(spec.type) {
        case FIELD:
             DataType ft=spec.getFieldType();
             if(ft==DataType.TIMESTAMP) 
                wp=new LongFieldBasedWP(spec);
             else if (ft==DataType.INT)
                 wp=new IntFieldBasedWP(spec);
             else 
                 throw new RuntimeException("datatype "+ft+" not supported for field based windows");
             break;
        case TIME:
        case TUPLES:
            default://TODO
            throw new RuntimeException("not implemented");
        }
        wp.aggList=aggList;
        wp.aggOutputDef=aggregateOutputDef;
        wp.aggInputDef=aggInputDef;
        wp.noOverlap=(spec.size.compareTo(spec.advance)<=0);
        return wp;
    }

    /**
     * When windows closes, returns a list with the tuples to be emitted, otherwise returns null
     */
    public List<Tuple> newData(Tuple tuple) {
        List<Tuple> ret=emptyReturn;

        if(windowTuples.isEmpty()) {
            setFirstTuple(tuple);
        } else if(isOutsideWindow(tuple)) {
            //windows closed
            if(aggList!=null) {
                Object[] v=new Object[aggList.size()];
                for(int i=0;i<aggList.size();i++) {
                    v[i]=aggList.get(i).getValue();
                }
                ret=new ArrayList<Tuple>(1);
                ret.add(new Tuple(aggOutputDef,v));
            } else {
                //TODO
            }
            if(noOverlap) {
                if(aggList!=null) {
                    for(CompiledAggregateExpression cae:aggList) {
                        cae.clear();
                    }
                } else {
                    ret=windowTuples;
                }
                windowTuples=new ArrayList<Tuple>();
                setFirstTuple(tuple);
            } else {
                //TODO
            }
        }
        windowTuples.add(tuple);
        if(aggList!=null) {
            for(CompiledAggregateExpression cae:aggList) {
                cae.newData(tuple);
            }
        }
        return ret;
    }

    abstract protected void setFirstTuple(Tuple tuple);
    abstract protected boolean isOutsideWindow(Tuple tuple);
}

class LongFieldBasedWP extends WindowProcessor {
    final long size,advance;
    long firstValue;
    final String field;
    public LongFieldBasedWP(WindowSpecification spec) {
        this.size=spec.size.longValue();
        this.advance=spec.advance.longValue();
        this.field=spec.field;
    }

    @Override
    protected boolean isOutsideWindow(Tuple tuple) {
        long nv=(Long)tuple.getColumn(field);
        return nv>=firstValue+size;
    }

    @Override
    protected void setFirstTuple(Tuple tuple) {
       firstValue=(Long)tuple.getColumn(field);
    }   
}

class IntFieldBasedWP extends WindowProcessor {
    final int size,advance;
    int firstValue;
    final String field;
    public IntFieldBasedWP(WindowSpecification spec) {
        this.size=spec.size.intValue();
        this.advance=spec.advance.intValue();
        this.field=spec.field;
    }

    @Override
    protected boolean isOutsideWindow(Tuple tuple) {
        int nv=(Integer)tuple.getColumn(field);
        return nv>=(firstValue+size);
    }

    @Override
    protected void setFirstTuple(Tuple tuple) {
       firstValue=(Integer)tuple.getColumn(field);
    }
}


