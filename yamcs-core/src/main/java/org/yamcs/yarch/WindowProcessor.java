package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.yarch.streamsql.WindowSpecification;

public abstract class WindowProcessor {
    protected List<CompiledAggregateExpression> aggList;
    TupleDefinition aggOutputDef;
    final static protected List<Tuple> EMPTY_RETURN = new ArrayList<>(0);
    public TupleDefinition aggInputDef;

    public static WindowProcessor getInstance(WindowSpecification spec, TupleDefinition aggInputDef,
            List<CompiledAggregateExpression> aggList, TupleDefinition aggregateOutputDef) {
        WindowProcessor wp;
        switch (spec.type) {
        case FIELD:
            DataType ft = spec.getFieldType();
            if (ft == DataType.TIMESTAMP)
                wp = new LongFieldBasedWP(spec);
            else if (ft == DataType.INT)
                wp = new IntFieldBasedWP(spec);
            else
                throw new IllegalArgumentException("datatype " + ft + " not supported for field based windows");
            break;
        case INFINITE:
            wp = new InfiniteWindowProcessor(spec);
            break;
        case TIME:
        case TUPLES:
        default:
            throw new IllegalArgumentException(spec.type + " not implemented");
        }
        wp.aggList = aggList;
        wp.aggOutputDef = aggregateOutputDef;
        wp.aggInputDef = aggInputDef;

        return wp;
    }

    public abstract List<Tuple> newData(Tuple tuple);

    /**
     * Called when the input stream closes, the window has the opportunity to emit some tuples before closure
     */
    protected abstract List<Tuple> streamClosed();
}

abstract class SlidingWindowProcessor extends WindowProcessor {
    List<Tuple> windowTuples = new ArrayList<>();
    boolean noOverlap; // if there is no overlap between the windows, some optimizations are possible

    public SlidingWindowProcessor(WindowSpecification spec) {
        this.noOverlap = (spec.size.compareTo(spec.advance) <= 0);
    }
    /**
     * When windows closes, returns a list with the tuples to be emitted, otherwise returns null
     */
    public List<Tuple> newData(Tuple tuple) {
        List<Tuple> ret = EMPTY_RETURN;

        if (windowTuples.isEmpty()) {
            setFirstTuple(tuple);
        } else if (isOutsideWindow(tuple)) {
            // windows closed
            if (aggList != null) {
                Object[] v = new Object[aggList.size()];
                for (int i = 0; i < aggList.size(); i++) {
                    v[i] = aggList.get(i).getValue();
                }
                ret = Arrays.asList(new Tuple(aggOutputDef, v));
            } else {// TODO
                throw new IllegalStateException("not implemented");
            }
            if (noOverlap) {
                if (aggList != null) {
                    for (CompiledAggregateExpression cae : aggList) {
                        cae.clear();
                    }
                } else {
                    ret = windowTuples;
                }
                windowTuples = new ArrayList<Tuple>();
                setFirstTuple(tuple);
            } else {// TODO
                throw new IllegalStateException("not implemented");
            }
        }
        windowTuples.add(tuple);
        if (aggList != null) {
            for (CompiledAggregateExpression cae : aggList) {
                cae.newData(tuple);
            }
        }
        return ret;
    }

    @Override
    protected List<Tuple> streamClosed() {
        return EMPTY_RETURN;
    }

    abstract protected void setFirstTuple(Tuple tuple);

    abstract protected boolean isOutsideWindow(Tuple tuple);
}

class LongFieldBasedWP extends SlidingWindowProcessor {
    final long size, advance;
    long firstValue;
    final String field;

    public LongFieldBasedWP(WindowSpecification spec) {
        super(spec);
        this.size = spec.size.longValue();
        this.advance = spec.advance.longValue();
        this.field = spec.field;
    }

    @Override
    protected boolean isOutsideWindow(Tuple tuple) {
        long nv = (Long) tuple.getColumn(field);
        return nv >= firstValue + size;
    }

    @Override
    protected void setFirstTuple(Tuple tuple) {
        firstValue = (Long) tuple.getColumn(field);
    }
}

class IntFieldBasedWP extends SlidingWindowProcessor {
    final int size, advance;
    int firstValue;
    final String field;

    public IntFieldBasedWP(WindowSpecification spec) {
        super(spec);
        this.size = spec.size.intValue();
        this.advance = spec.advance.intValue();
        this.field = spec.field;
    }

    @Override
    protected boolean isOutsideWindow(Tuple tuple) {
        int nv = (Integer) tuple.getColumn(field);
        return nv >= (firstValue + size);
    }

    @Override
    protected void setFirstTuple(Tuple tuple) {
        firstValue = (Integer) tuple.getColumn(field);
    }
}


class InfiniteWindowProcessor extends WindowProcessor {

    public InfiniteWindowProcessor(WindowSpecification spec) {
    }

    @Override
    public List<Tuple> newData(Tuple tuple) {
        if (aggList != null) {
            for (CompiledAggregateExpression cae : aggList) {
                cae.newData(tuple);
            }
        }
        return EMPTY_RETURN;
    }

    @Override
    protected List<Tuple> streamClosed() {
        List<Tuple> ret = EMPTY_RETURN;
        if (aggList != null) {
            Object[] v = new Object[aggList.size()];
            for (int i = 0; i < aggList.size(); i++) {
                v[i] = aggList.get(i).getValue();
            }
            ret = Arrays.asList(new Tuple(aggOutputDef, v));
        }
        return ret;
    }

}
